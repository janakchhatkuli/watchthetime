package com.watchthetime.domain

import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.event.ClockStopped
import com.watchthetime.domain.event.Stamp
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.state.Countdown
import com.watchthetime.domain.state.GameStatus
import kotlin.test.*

class ClockTest {

    @Test
    fun countdownIsComputedNotTicked() {
        val t0 = Stamp(1000, 50_000, 1)
        val c = Countdown(10_000).start(t0)
        assertEquals(10_000, c.at(t0))
        assertEquals(6_500, c.at(t0.plus(3_500)))
        assertEquals(0, c.at(t0.plus(99_000)), "never negative")
        val frozen = c.freeze(t0.plus(2_000))
        assertFalse(frozen.running)
        assertEquals(8_000, frozen.at(t0.plus(50_000)))
    }

    @Test
    fun elapsedFallsBackToWallClockAcrossReboot() {
        val before = Stamp(monoMs = 900_000, wallMs = 10_000, boot = 3)
        val after = Stamp(monoMs = 4_000, wallMs = 17_000, boot = 4)
        assertEquals(7_000, after.elapsedSince(before))
    }

    @Test
    fun formatting() {
        assertEquals("12:00", ClockFormat.game(720_000))
        assertEquals("11:59", ClockFormat.game(719_999), "floored like a real clock")
        assertEquals("!9:05", ClockFormat.game(545_000))
        assertEquals("!1:00", ClockFormat.game(60_000))
        assertEquals("59.9", ClockFormat.game(59_999), "tenths in last minute")
        assertEquals("!0.0", ClockFormat.game(0))
        assertEquals("!0:59", ClockFormat.game(59_999, tenths = false))
        assertEquals("24", ClockFormat.shot(24_000))
        assertEquals("!9", ClockFormat.shot(9_400))
        assertEquals("4.9", ClockFormat.shot(4_999))
        assertEquals("88:88", ClockFormat.ghost("!9:05"))
        assertEquals("1:15", ClockFormat.countdown(75_000))
        assertEquals("!1", ClockFormat.countdown(1))
        assertEquals("0:42.3", ClockFormat.plain(42_300, tenths = true))
    }

    @Test
    fun startStopResumeIsAccurate() {
        val (s, t) = Fixtures.session()
        assertEquals(GameStatus.PREGAME, s.state.status)
        assertTrue(s.dispatch(Command.StartClock).ok)
        t.advance(30_250)
        assertEquals(689_750, s.state.clock.at(t.now()))
        s.dispatch(Command.StopClock)
        t.advance(60_000) // stopped: no time passes
        assertEquals(689_750, s.state.clock.at(t.now()))
        s.dispatch(Command.ToggleClock)
        t.advance(750)
        assertEquals(689_000, s.state.clock.at(t.now()))
        assertEquals(GameStatus.LIVE, s.state.status)
    }

    @Test
    fun manualAdjustUpAndDownWhileRunningAndStopped() {
        val (s, t) = Fixtures.session()
        s.dispatch(Command.StartClock)
        t.advance(10_000)
        s.dispatch(Command.AdjustClock(+1_000))
        assertEquals(711_000, s.state.clock.at(t.now()))
        s.dispatch(Command.AdjustClock(-10_000))
        assertEquals(701_000, s.state.clock.at(t.now()))
        t.advance(1_000)
        assertEquals(700_000, s.state.clock.at(t.now()), "still running after adjust")
        s.dispatch(Command.StopClock)
        s.dispatch(Command.AdjustClock(+100_000))
        assertEquals(720_000, s.state.clock.at(t.now()), "clamped to period length")
        s.dispatch(Command.AdjustClock(-900_000))
        assertEquals(0, s.state.clock.at(t.now()), "clamped to zero")
    }

    @Test
    fun buzzerFiresExactlyAtZeroAndStopsClock() {
        val (s, t) = Fixtures.session(Rules(periodLengthMs = 5 * 60_000L, shotClockEnabled = false))
        s.dispatch(Command.StartClock)
        t.advance(299_999)
        assertTrue(s.tick().cues.none { it.type == CueType.PERIOD_END })
        t.advance(1_000) // tick arrives late — remaining must still be exactly 0
        val out = s.tick()
        assertTrue(out.cues.any { it.type == CueType.PERIOD_END })
        assertTrue(out.changed.single().payload == ClockStopped(expired = true))
        assertEquals(0, s.state.clock.at(t.now()))
        assertEquals(GameStatus.PERIOD_BREAK, s.state.status)
        assertTrue(s.tick().cues.isEmpty(), "buzzer fires once")
        // Clock can't start in a dead period.
        assertFalse(s.dispatch(Command.StartClock).ok)
    }

    @Test
    fun lastMinuteWarningOncePerCrossing() {
        val (s, t) = Fixtures.session(Rules(periodLengthMs = 2 * 60_000L, shotClockEnabled = false))
        s.dispatch(Command.StartClock)
        t.advance(59_000); assertTrue(s.tick().cues.isEmpty())
        t.advance(1_500)
        assertEquals(listOf(CueType.LAST_MINUTE), s.tick().cues.map { it.type })
        t.advance(1_000); assertTrue(s.tick().cues.isEmpty())
        // Correcting the clock back above a minute re-arms the warning.
        s.dispatch(Command.AdjustClock(10_000))
        t.advance(10_000)
        assertEquals(listOf(CueType.LAST_MINUTE), s.tick().cues.map { it.type })
    }

    @Test
    fun deadlineSchedulingPointsAtNextEvent() {
        val (s, t) = Fixtures.session()
        assertNull(s.millisToNextDeadline())
        s.dispatch(Command.StartClock)
        assertEquals(24_000, s.millisToNextDeadline(), "shot clock is the nearest deadline")
        s.dispatch(Command.ToggleShotHold)
        assertEquals(720_000 - 60_000, s.millisToNextDeadline())
    }

    @Test
    fun shotClockFollowsGameClockResetsAndExpires() {
        val (s, t) = Fixtures.session()
        s.dispatch(Command.StartClock)
        t.advance(10_000)
        assertEquals(14_000, s.state.shot.at(t.now()))
        s.dispatch(Command.ResetShotClock(short = false))
        assertEquals(24_000, s.state.shot.at(t.now()))
        t.advance(4_000)
        s.dispatch(Command.StopClock)
        t.advance(10_000)
        assertEquals(20_000, s.state.shot.at(t.now()), "shot clock stops with game clock")
        s.dispatch(Command.ResetShotClock(short = true))
        assertEquals(14_000, s.state.shot.at(t.now()))
        s.dispatch(Command.StartClock)
        t.advance(14_200)
        val out = s.tick()
        assertTrue(out.cues.any { it.type == CueType.SHOT_CLOCK_EXPIRED })
        assertFalse(s.state.clock.running, "violation stops game clock by default")
        assertEquals(720_000 - 14_000 - 14_000, s.state.clock.baseMs, "stopped at the shot deadline, not the late tick")
    }

    @Test
    fun timeoutCountdownEndsAutomaticallyAndStopsClock() {
        val (s, t) = Fixtures.session()
        s.dispatch(Command.StartClock)
        t.advance(5_000)
        val out = s.dispatch(Command.StartTimeout(com.watchthetime.domain.model.TeamSide.HOME))
        assertTrue(out.ok)
        assertFalse(s.state.clock.running)
        assertEquals(6, s.state.timeoutsRemaining(com.watchthetime.domain.model.TeamSide.HOME))
        t.advance(74_000); assertTrue(s.tick().cues.isEmpty())
        t.advance(1_000)
        assertTrue(s.tick().cues.any { it.type == CueType.TIMEOUT_END })
        assertNull(s.state.timeout)
    }

    @Test
    fun clockSurvivesRebootViaWallClock() {
        val (s, t) = Fixtures.session()
        s.dispatch(Command.StartClock)
        t.advance(20_000)
        t.reboot()
        t.wall += 5_000 // 5 s pass during reboot
        assertEquals(695_000, s.state.clock.at(t.now()))
    }

    @Test
    fun periodsAdvanceToOvertimeOnlyWhenTied() {
        val (s, t) = Fixtures.session(Rules(periodLengthMs = 60_000, shotClockEnabled = false))
        repeat(3) { s.dispatch(Command.NextPeriod) }
        assertEquals(4, s.state.period)
        s.dispatch(Command.NextPeriod) // tied 0–0
        assertEquals(5, s.state.period)
        assertEquals("OT1", ClockFormat.periodShort(5, s.state.rules))
        assertEquals(5 * 60_000L, s.state.clock.baseMs, "overtime length applied")
        s.dispatch(Command.AddScore(com.watchthetime.domain.model.TeamSide.AWAY, 2))
        s.dispatch(Command.NextPeriod)
        assertEquals(GameStatus.FINAL, s.state.status)
        assertFalse(s.dispatch(Command.AddScore(com.watchthetime.domain.model.TeamSide.HOME, 2)).ok)
        s.dispatch(Command.Undo)
        assertEquals(GameStatus.LIVE, s.state.status, "undo of 'final' restores the overtime period")
        assertEquals(5, s.state.period)
        assertEquals(5 * 60_000L, s.state.clock.baseMs)
    }
}
