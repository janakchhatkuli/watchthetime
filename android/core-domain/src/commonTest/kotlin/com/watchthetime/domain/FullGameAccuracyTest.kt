package com.watchthetime.domain

import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.engine.GameSession
import com.watchthetime.domain.event.ShotType
import com.watchthetime.domain.model.TeamSide.AWAY
import com.watchthetime.domain.model.TeamSide.HOME
import com.watchthetime.domain.rules.ClockMode
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.RulePreset
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.state.GameStatus
import com.watchthetime.domain.state.GameState
import kotlin.random.Random
import kotlin.test.*

/**
 * Simulates complete games the way the live clock service would: advance wall time in
 * irregular slices, tick whenever the service would wake, and assert the reconstructed
 * scoreboard (score, clock, fouls, timeouts, status) matches hand-checked expectations.
 */
class FullGameAccuracyTest {

    /** Drive the session like the foreground service; returns every cue type seen. */
    private fun runClock(s: GameSession, t: FakeTime, ms: Long, rng: Random): Set<CueType> {
        var left = ms
        val seen = mutableSetOf<CueType>()
        while (left > 0) {
            val step = minOf(left, listOf(100L, 250L, 500L, 999L, 1_000L, 2_500L)[rng.nextInt(6)])
            t.advance(step); left -= step
            s.tick().cues.mapTo(seen) { it.type }
        }
        return seen
    }

    @Test
    fun fibaGameFourQuartersWithTimeoutsAndFouls() {
        val rng = Random(42)
        val (s, t) = Fixtures.session(
            Rules.preset(RulePreset.FIBA),
            policy = ClockPolicy(ClockMode.RUNNING),
        )
        assertEquals(600_000, s.state.clock.baseMs)
        assertEquals(GameStatus.PREGAME, s.state.status)

        // ---- Q1 -------------------------------------------------------------------
        s.dispatch(Command.StartClock)
        runClock(s, t, 45_000, rng)
        s.dispatch(Command.AddScore(HOME, 2, "h4"))
        runClock(s, t, 90_000, rng)
        s.dispatch(Command.AddScore(AWAY, 3, "a11"))
        runClock(s, t, 120_000, rng)
        s.dispatch(Command.AddFoul(HOME, "h7"))
        s.dispatch(Command.AddScore(AWAY, 2))
        assertEquals(1, s.state.teamFouls(HOME), "1 team foul in Q1")
        runClock(s, t, 250_000, rng)
        assertEquals(GameStatus.LIVE, s.state.status)
        assertTrue(s.dispatch(Command.EndPeriod).ok)
        assertEquals(GameStatus.PERIOD_BREAK, s.state.status, "Q1 ends")
        assertEquals(1, s.state.teamFouls(HOME), "fouls stay until the next period")
        assertEquals(120_000, s.state.intervalRemaining(t.now()))
        assertEquals(2, s.state.pointsIn(HOME, 1))
        assertEquals(5, s.state.pointsIn(AWAY, 1))
        assertTrue(s.state.team(HOME).timeoutsTaken.isEmpty())

        val endInterval = runClock(s, t, 120_000, rng)
        assertTrue(CueType.INTERVAL_END in endInterval)
        s.dispatch(Command.NextPeriod)
        assertEquals(2, s.state.period)
        assertEquals(600_000, s.state.clock.baseMs)
        assertEquals(0, s.state.teamFouls(HOME), "team fouls reset each quarter")

        // ---- Q2: timeout lifecycle, bonus, then end the period explicitly --------------------
        s.dispatch(Command.StartClock)
        s.dispatch(Command.StartTimeout(HOME))
        assertEquals(1, s.state.timeoutsRemaining(HOME), "2 in the first half, 1 used")
        assertTrue(s.tick().cues.none { it.type == CueType.TIMEOUT_WARNING }, "no warning yet")
        assertTrue(CueType.TIMEOUT_WARNING in runClock(s, t, 51_000, rng), "50 s warning")
        assertTrue(CueType.TIMEOUT_END in runClock(s, t, 10_000, rng))
        assertEquals(1, s.state.timeoutsRemaining(HOME))
        s.dispatch(Command.StartClock) // user taps after the timeout
        repeat(4) { s.dispatch(Command.AddFoul(AWAY, "a0")) }
        assertEquals(4, s.state.teamFouls(AWAY))
        assertEquals("Bonus", s.state.bonusText(HOME), "5th team foul is the bonus")
        s.dispatch(Command.AddScore(HOME, 1, "h23", shot = ShotType.FREE_THROW))
        assertTrue(s.dispatch(Command.EndPeriod).ok)
        assertEquals(GameStatus.PERIOD_BREAK, s.state.status)
        assertEquals(900_000, s.state.intervalRemaining(t.now())!!, "half-time interval starts when Q2 ends")

        // ---- Half-time and Q3/Q4 --------------------------------------------------
        assertTrue(CueType.INTERVAL_END in runClock(s, t, 900_000, rng))
        s.dispatch(Command.NextPeriod)
        assertEquals(3, s.state.period)
        s.dispatch(Command.StartClock)
        assertTrue(s.dispatch(Command.EndPeriod).ok)
        s.dispatch(Command.NextPeriod)
        assertEquals(4, s.state.period)

        // ---- Q4 late game: FIBA last-2:00 cap ------------------------------------------------
        s.dispatch(Command.SetClock(119_000))
        s.dispatch(Command.StartClock)
        s.dispatch(Command.StartTimeout(AWAY)); s.dispatch(Command.EndTimeout)
        s.dispatch(Command.StartTimeout(AWAY)); s.dispatch(Command.EndTimeout)
        val third = s.dispatch(Command.StartTimeout(AWAY))
        assertFalse(third.ok, "max 2 timeouts at ≤2:00 in Q4")
        assertEquals(0, s.state.timeoutsAvailable(AWAY, s.state.clock.at(t.now())), "both late slots used")
        assertEquals(1, s.state.timeoutsRemaining(AWAY), "allocation still has 1 left, blocked by the cap")

        s.dispatch(Command.StartClock) // resume after the timeouts
        assertTrue(s.dispatch(Command.EndPeriod).ok)
        assertEquals(GameStatus.PERIOD_BREAK, s.state.status)

        // Not tied: game ends.
        val home = s.state.team(HOME).score
        val away = s.state.team(AWAY).score
        assertNotEquals(home, away, "test game is not tied")
        s.dispatch(Command.NextPeriod)
        assertEquals(GameStatus.FINAL, s.state.status)
        assertTrue(s.state.finalized)

        // ---- Undo across the finish reopens, and totals stay consistent -------------
        val beforeUndo = s.state
        s.dispatch(Command.Undo)
        assertFalse(s.state.finalized)
        assertEquals(GameStatus.PERIOD_BREAK, s.state.status)
        s.dispatch(Command.Redo)
        assertTrue(s.state.finalized)
        assertEquals(beforeUndo, s.state, "redo restores the final state exactly")

        // Score by period sums to the total.
        val periods = s.state.rules.regulationPeriods
        for (side in listOf(HOME, AWAY)) {
            assertEquals(s.state.team(side).score, (1..periods).sumOf { s.state.pointsIn(side, it) }, "per-period sums")
        }
    }

    @Test
    fun undoAfterEveryEventRestoresExactState() {
        val rng = Random(7)
        val (s, t) = Fixtures.session(
            Rules.preset(RulePreset.FIBA),
            policy = ClockPolicy(ClockMode.RUNNING),
        )
        val history = mutableListOf<GameState>()
        var undos = 0
        for (round in 0 until 24) {
            runClock(s, t, listOf(5_000L, 20_000L, 90_000L)[rng.nextInt(3)], rng)
            val before = s.state
            val action: () -> Boolean = when (round % 8) {
                0 -> ({ s.dispatch(Command.AddScore(HOME, 2, "h4")).ok })
                1 -> ({ s.dispatch(Command.AddScore(AWAY, 3, "a11")).ok })
                2 -> ({ s.dispatch(Command.AddFoul(HOME, "h7")).ok })
                3 -> ({ s.dispatch(Command.AddFoul(AWAY, null)).ok })
                4 -> ({ s.dispatch(Command.AddFreeThrows(HOME, "h4", 2, 1)).ok })
                5 -> ({ s.dispatch(Command.AddSubstitution(HOME, "h7", "h23")).ok })
                6 -> ({ s.dispatch(Command.StartTimeout(HOME)).ok })
                else -> ({ s.dispatch(Command.EndTimeout).ok })
            }
            history += before
            if (!action()) {
                history.removeAt(history.lastIndex)
                continue
            }
            undos++
            assertTrue(s.dispatch(Command.Undo).ok, "undo #$undos")
            assertEquals(before, s.state, "undo #$undos restores state")
        }
        // After undoing everything we can, redo all of it and land back on the final state.
        val finalBeforeRedo = s.state
        var rewound = 0
        while (s.dispatch(Command.Redo).ok) rewound++
        assertTrue(rewound > 0, "at least one redo")
        assertNotEquals(finalBeforeRedo, s.state, "redo advances the state")
    }

    @Test
    fun rebootMidGameKeepsScoreAndClock() {
        val (s, t) = Fixtures.session(Rules.preset(RulePreset.FIBA), policy = ClockPolicy(ClockMode.STOPPING))
        s.dispatch(Command.StartClock)
        s.dispatch(Command.AddScore(HOME, 2)) // stops in stopping mode
        s.dispatch(Command.ToggleClock)
        runClock(s, t, 47_000, Random(1))
        val before = s.state
        val events = s.allEvents
        t.reboot(5_000)

        var n = 0
        val restored = GameSession("g1", events, t, "watch", newId = { "r${++n}" })
        assertEquals(before, restored.state, "reboot from the event log reproduces the state")
        restored.tick()
        assertTrue(restored.tick().cues.isEmpty(), "no phantom cues after reboot")
        restored.dispatch(Command.AddScore(AWAY, 3, "a11"))
        assertEquals(2, restored.state.team(HOME).score)
        assertEquals(3, restored.state.team(AWAY).score)
    }

    @Test
    fun threeByThreeFullGameFirstTo21OrTenMinutes() {
        val rng = Random(99)
        val (s, t) = Fixtures.session(
            Rules.preset(RulePreset.FIBA_3X3),
            policy = ClockPolicy(ClockMode.RUNNING),
        )
        s.dispatch(Command.StartClock)
        var side = HOME
        while (!s.state.finalized) {
            if (runClock(s, t, 8_000 + rng.nextInt(10_000).toLong(), rng).any { it == CueType.GAME_FINAL || it == CueType.PERIOD_END }) {
                // Period may have ended (tied -> break; ahead -> final via tick).
                if (s.state.status == GameStatus.PERIOD_BREAK) {
                    s.dispatch(Command.NextPeriod) // overtime
                    s.dispatch(Command.StartClock)
                }
            }
            if (s.state.finalized) break
            s.dispatch(Command.AddScore(side, if (rng.nextBoolean()) 2 else 1, shot = ShotType.FIELD_GOAL))
            side = if (side == HOME) AWAY else HOME
        }
        val maxScore = maxOf(s.state.team(HOME).score, s.state.team(AWAY).score)
        val clockRanOut = s.state.period >= s.state.rules.regulationPeriods && s.state.clock.baseMs == 0L
        assertTrue(maxScore >= 21 || clockRanOut, "decided by 21 or by the clock")
        assertEquals(GameStatus.FINAL, s.state.status)
        assertNotEquals(s.state.team(HOME).score, s.state.team(AWAY).score)

        // Undo the winning basket (or final whistle) reopens the game.
        s.dispatch(Command.Undo)
        assertFalse(s.state.finalized)
        assertTrue(maxOf(s.state.team(HOME).score, s.state.team(AWAY).score) < maxScore)
    }

    @Test
    fun nbaScoreStopWindowsMatchThePreset() {
        val (s, t) = Fixtures.session(
            Rules.preset(RulePreset.NBA),
            policy = ClockPolicy(ClockMode.RUNNING, runningStopsLate = true),
        )
        repeat(3) { s.dispatch(Command.NextPeriod) } // Q4
        s.dispatch(Command.SetClock(150_000)) // 2:30, outside the 1:00 window
        s.dispatch(Command.StartClock)
        s.dispatch(Command.AddScore(HOME, 2))
        assertTrue(s.state.clock.running, "running mode + outside stop window: clock keeps going")

        s.dispatch(Command.SetClock(50_000)) // 0:50 inside 1:00
        val stopped = s.dispatch(Command.AddScore(AWAY, 3))
        assertFalse(s.state.clock.running, "last 1:00 of Q4 stops the clock on a score")
        assertTrue(stopped.cues.any { it.type == CueType.CLOCK_STOP })
    }
}
