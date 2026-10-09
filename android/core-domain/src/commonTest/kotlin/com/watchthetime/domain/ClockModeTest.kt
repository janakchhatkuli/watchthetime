package com.watchthetime.domain

import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.engine.GameSession
import com.watchthetime.domain.event.ClockPolicyChanged
import com.watchthetime.domain.event.ClockStopped
import com.watchthetime.domain.model.TeamSide.AWAY
import com.watchthetime.domain.model.TeamSide.HOME
import com.watchthetime.domain.rules.ClockMode
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.RulePreset
import com.watchthetime.domain.rules.Rules
import kotlin.test.*

class ClockModeTest {

    private val stopping = ClockPolicy(ClockMode.STOPPING)
    private val running = ClockPolicy(ClockMode.RUNNING)

    /** Advances time and runs the tick loop many times, as the clock service would. */
    private fun idle(s: GameSession, t: FakeTime, ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 250)
            t.advance(step); left -= step
            s.tick()
        }
    }

    // ---- Stopping time -----------------------------------------------------------------

    @Test
    fun stoppingPausesOnScoreAndNeverAutoResumes() {
        val (s, t) = Fixtures.session(policy = stopping)
        s.dispatch(Command.StartClock)
        t.advance(10_000)
        val out = s.dispatch(Command.AddScore(HOME, 2))
        assertTrue(out.ok)
        assertFalse(s.state.clock.running, "score pauses the clock")
        assertEquals(710_000, s.state.clock.baseMs)
        assertEquals(CueType.SCORE_2, out.cues.first().type, "the score's own sound plays first")
        assertTrue(out.cues.any { it.type == CueType.CLOCK_STOP })
        assertTrue(out.changed.any { (it.payload as? ClockStopped)?.auto == true })

        idle(s, t, 120_000)
        assertFalse(s.state.clock.running, "never restarts on its own")
        assertEquals(710_000, s.state.clock.at(t.now()))

        s.dispatch(Command.ToggleClock) // the user's tap
        t.advance(1_000)
        assertEquals(709_000, s.state.clock.at(t.now()))
    }

    @Test
    fun stoppingPausesOnFoulAndTimeoutAndStaysPausedAfterTimeoutEnds() {
        val (s, t) = Fixtures.session(policy = stopping)
        s.dispatch(Command.StartClock)
        t.advance(5_000)
        s.dispatch(Command.AddFoul(AWAY, "a0"))
        assertFalse(s.state.clock.running, "foul pauses")

        s.dispatch(Command.StartClock)
        t.advance(5_000)
        s.dispatch(Command.StartTimeout(HOME))
        assertFalse(s.state.clock.running, "timeout pauses")
        idle(s, t, 80_000) // timeout (75 s) runs out
        assertNull(s.state.timeout, "timeout ended by itself")
        assertFalse(s.state.clock.running, "...but the game clock does not restart")
        assertEquals(710_000, s.state.clock.at(t.now()))
    }

    @Test
    fun stoppingWithClockAlreadyStoppedWritesNoExtraStop() {
        val (s, _) = Fixtures.session(policy = stopping)
        val out = s.dispatch(Command.AddScore(HOME, 3))
        assertTrue(out.changed.none { it.payload is ClockStopped })
        assertTrue(out.cues.none { it.type == CueType.CLOCK_STOP })
    }

    @Test
    fun stoppingScoresOnlyLateUsesThePresetWindow() {
        val fiba = Rules.preset(RulePreset.FIBA)
        val (s, t) = Fixtures.session(fiba, policy = stopping.copy(scoresStopOnlyLate = true))
        repeat(3) { s.dispatch(Command.NextPeriod) } // Q4
        s.dispatch(Command.StartClock)
        t.advance(60_000) // 9:00 left
        s.dispatch(Command.AddScore(HOME, 2))
        assertTrue(s.state.clock.running, "Q4 9:00: a score does not stop the clock")
        s.dispatch(Command.AddFoul(AWAY, null))
        assertFalse(s.state.clock.running, "fouls always stop in stopping time")

        s.dispatch(Command.SetClock(120_000)) // exactly 2:00 counts as inside the window
        s.dispatch(Command.StartClock)
        s.dispatch(Command.AddScore(HOME, 2))
        assertFalse(s.state.clock.running, "Q4 2:00: a score stops the clock")
    }

    // ---- Running time ------------------------------------------------------------------

    @Test
    fun runningNeverPausesOnScoreOrFoul() {
        val (s, t) = Fixtures.session(policy = running)
        s.dispatch(Command.StartClock)
        t.advance(1_000)
        s.dispatch(Command.AddScore(HOME, 2))
        s.dispatch(Command.AddScore(AWAY, 3))
        s.dispatch(Command.AddFoul(HOME, "h4"))
        s.dispatch(Command.AddFoul(AWAY, null, com.watchthetime.domain.model.FoulType.TECHNICAL))
        assertTrue(s.state.clock.running)
        t.advance(1_000)
        assertEquals(718_000, s.state.clock.at(t.now()), "no time lost")
        assertTrue(s.allEvents.none { it.payload is ClockStopped })
    }

    @Test
    fun runningStopsOnTimeoutAndPeriodEnd() {
        val (s, t) = Fixtures.session(Rules(periodLengthMs = 60_000, shotClockEnabled = false), policy = running)
        s.dispatch(Command.StartClock)
        t.advance(10_000)
        s.dispatch(Command.StartTimeout(HOME))
        assertFalse(s.state.clock.running, "timeout stops in running time")
        s.dispatch(Command.StartClock) // ends timeout and resumes
        assertTrue(s.state.clock.running)
        idle(s, t, 60_000)
        assertFalse(s.state.clock.running, "period end stops")
        assertEquals(0, s.state.clock.at(t.now()))
    }

    @Test
    fun runningOptionalLateStopFollowsPresetWindow() {
        // NBA: last minute of Q1-3, last 2:00 of Q4/OT.
        val (s, t) = Fixtures.session(Rules.preset(RulePreset.NBA), policy = running.copy(runningStopsLate = true))
        s.dispatch(Command.SetClock(61_000))
        s.dispatch(Command.StartClock)
        s.dispatch(Command.AddScore(HOME, 2))
        assertTrue(s.state.clock.running, "Q1 1:01: outside the window")
        t.advance(2_000)
        s.dispatch(Command.AddScore(HOME, 2))
        assertFalse(s.state.clock.running, "Q1 0:59: inside the window")
    }

    // ---- Mode changes ------------------------------------------------------------------

    @Test
    fun modeChangeIsLoggedUndoableAndTakesEffect() {
        val (s, t) = Fixtures.session(policy = running)
        s.dispatch(Command.StartClock)
        val out = s.dispatch(Command.SetClockPolicy(stopping))
        assertTrue(out.ok)
        assertEquals(CueType.MODE_CHANGED, out.cues.first().type)
        assertTrue(s.allEvents.any { it.payload == ClockPolicyChanged(stopping) }, "change is in the event log")
        assertTrue(s.state.clock.running, "changing mode does not touch the clock")

        s.dispatch(Command.AddScore(HOME, 2))
        assertFalse(s.state.clock.running, "now in stopping time")
        s.dispatch(Command.Undo) // undo the score (and its auto-stop)
        s.dispatch(Command.Undo) // undo the mode change
        assertEquals(ClockMode.RUNNING, s.state.clockPolicy.mode)
        assertTrue(s.state.clock.running)
        t.advance(500)
        s.dispatch(Command.AddScore(HOME, 2))
        assertTrue(s.state.clock.running, "back in running time")
        assertFalse(s.dispatch(Command.SetClockPolicy(running)).ok, "no-op change is rejected")
    }

    @Test
    fun undoOfAnAutoStoppedScoreRestoresTheRunningClock() {
        val (s, t) = Fixtures.session(policy = stopping)
        s.dispatch(Command.StartClock)
        t.advance(10_000)
        s.dispatch(Command.AddScore(HOME, 2))
        t.advance(3_000)
        s.dispatch(Command.Undo)
        assertEquals(0, s.state.team(HOME).score)
        assertTrue(s.state.clock.running, "score and its pause are one transaction")
        assertEquals(707_000, s.state.clock.at(t.now()), "the clock kept its original anchor")
    }

    @Test
    fun deletingAScoreInTheLogKeepsTheRecordedPause() {
        // The pause really happened on court; removing the score later must not rewrite time.
        val (s, t) = Fixtures.session(policy = stopping)
        s.dispatch(Command.StartClock)
        t.advance(10_000)
        val score = s.dispatch(Command.AddScore(HOME, 2)).changed.first { it.payload is com.watchthetime.domain.event.Score }
        s.dispatch(Command.DeleteEvent(score.id))
        assertEquals(0, s.state.team(HOME).score)
        assertFalse(s.state.clock.running)
        assertEquals(710_000, s.state.clock.at(t.now()))
    }

    @Test
    fun oldGamesWithoutAPolicyReplayAsRunningTime() {
        val json = """{"rules":{},"home":{"name":"H","shortName":"H","colorArgb":0},"away":{"name":"A","shortName":"A","colorArgb":1}}"""
        val created = com.watchthetime.domain.sync.WireJson.decodeFromString(
            com.watchthetime.domain.event.GameCreated.serializer(), json,
        )
        assertEquals(ClockMode.RUNNING, created.clockPolicy.mode)
    }
}
