package com.watchthetime.domain

import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.event.ShotType
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.model.TeamSide.AWAY
import com.watchthetime.domain.model.TeamSide.HOME
import com.watchthetime.domain.rules.ClockMode
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.RulePreset
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.state.GameStatus
import com.watchthetime.domain.state.PenaltyLevel
import kotlin.test.*

class RulesEngineTest {

    private fun foul(side: TeamSide, player: String?, type: FoulType = FoulType.PERSONAL) = Command.AddFoul(side, player, type)

    @Test
    fun presetsOfferedForNewGames() {
        assertEquals(
            listOf(RulePreset.NBA, RulePreset.FIBA, RulePreset.NCAA, RulePreset.NCAA_W, RulePreset.FIBA_3X3),
            Rules.selectablePresets, "NFHS removed from new games",
        )
        // Headline values per preset (sources in RULES.md).
        with(Rules.preset(RulePreset.FIBA)) { assertEquals(4, regulationPeriods); assertEquals(600_000, periodLengthMs); assertEquals(5, playerFoulLimit) }
        with(Rules.preset(RulePreset.NBA)) { assertEquals(4, regulationPeriods); assertEquals(720_000, periodLengthMs); assertEquals(6, playerFoulLimit) }
        with(Rules.preset(RulePreset.NCAA)) { assertEquals(2, regulationPeriods); assertEquals(1_200_000, periodLengthMs); assertEquals(30_000, shotClockFullMs) }
        with(Rules.preset(RulePreset.NCAA_W)) { assertEquals(4, regulationPeriods); assertEquals(600_000, periodLengthMs); assertEquals(5, bonusAt) }
        with(Rules.preset(RulePreset.FIBA_3X3)) { assertEquals(1, regulationPeriods); assertEquals(12_000, shotClockFullMs); assertEquals(listOf(1, 2), scoringPoints) }
    }

    // ---- NBA -----------------------------------------------------------------------------

    @Test
    fun nbaOffensiveFoulIsNotATeamFoulButCountsPersonal() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        s.dispatch(foul(HOME, "h4", FoulType.OFFENSIVE))
        assertEquals(0, s.state.teamFouls(HOME))
        assertEquals(1, s.state.player("h4")!!.countedFouls(s.state.rules))
    }

    @Test
    fun nbaFlagrantEjections() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        assertTrue(s.dispatch(foul(HOME, "h4", FoulType.FLAGRANT_2)).cues.any { it.type == CueType.FOUL_OUT }, "F2 ejects")
        s.dispatch(foul(AWAY, "a0", FoulType.FLAGRANT))
        assertFalse(s.state.player("a0")!!.disqualified(s.state.rules))
        assertTrue(s.dispatch(foul(AWAY, "a0", FoulType.FLAGRANT)).cues.any { it.type == CueType.FOUL_OUT }, "2 F1 eject")
        s.dispatch(foul(AWAY, "a11", FoulType.TECHNICAL_2)); s.dispatch(foul(AWAY, "a11", FoulType.TECHNICAL_2))
        assertFalse(s.state.player("a11")!!.disqualified(s.state.rules), "non-unsportsmanlike technicals never eject")
    }

    @Test
    fun nbaFourthQuarterTimeoutCaps() {
        val (s, t) = Fixtures.session(Rules.preset(RulePreset.NBA))
        repeat(3) { s.dispatch(Command.NextPeriod) } // Q4, 7 timeouts left
        fun take() = s.dispatch(Command.StartTimeout(HOME)).also { if (it.ok) s.dispatch(Command.EndTimeout) }
        repeat(2) { assertTrue(take().ok) } // at 12:00
        s.dispatch(Command.SetClock(170_000)) // 2:50, after the 3:00 mark
        repeat(2) { assertTrue(take().ok) }
        val third = take()
        assertFalse(third.ok, "max 2 after 3:00 / max 4 in Q4")
        assertEquals(3, s.state.timeoutsRemaining(HOME), "allocation still shows 3 left")
        assertEquals(0, s.state.timeoutsAvailable(HOME, s.state.clock.at(t.now())))
    }

    // ---- FIBA ----------------------------------------------------------------------------

    @Test
    fun fibaLastTwoMinutesOfQ4AllowsTwoTimeouts() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        repeat(3) { s.dispatch(Command.NextPeriod) }
        s.dispatch(Command.SetClock(119_000))
        assertTrue(s.dispatch(Command.StartTimeout(AWAY)).ok); s.dispatch(Command.EndTimeout)
        assertTrue(s.dispatch(Command.StartTimeout(AWAY)).ok); s.dispatch(Command.EndTimeout)
        assertFalse(s.dispatch(Command.StartTimeout(AWAY)).ok, "3rd 2nd-half timeout not allowed at ≤2:00")
    }

    @Test
    fun fibaTimeoutWarningAtFiftySeconds() {
        val (s, t) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        s.dispatch(Command.StartTimeout(HOME))
        t.advance(49_000); assertTrue(s.tick().cues.none { it.type == CueType.TIMEOUT_WARNING })
        t.advance(1_000); assertEquals(listOf(CueType.TIMEOUT_WARNING), s.tick().cues.map { it.type })
        t.advance(5_000); assertTrue(s.tick().cues.isEmpty(), "warns once")
        t.advance(5_000); assertTrue(s.tick().cues.any { it.type == CueType.TIMEOUT_END })
    }

    @Test
    fun intervalTimerAfterQuarterAndHalftime() {
        val (s, t) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        s.dispatch(Command.StartClock)
        t.advance(600_000); s.tick() // Q1 buzzer
        assertEquals(GameStatus.PERIOD_BREAK, s.state.status)
        assertEquals(120_000, s.state.intervalRemaining(t.now()))
        t.advance(119_000); assertTrue(s.tick().cues.none { it.type == CueType.INTERVAL_END })
        assertTrue(s.millisToNextDeadline()!! <= 1_000, "service wakes for the interval end")
        t.advance(1_000); assertEquals(listOf(CueType.INTERVAL_END), s.tick().cues.map { it.type })
        t.advance(1_000); assertTrue(s.tick().cues.isEmpty())

        s.dispatch(Command.NextPeriod); assertNull(s.state.intervalRemaining(t.now()))
        s.dispatch(Command.StartClock); t.advance(600_000); s.tick() // end of Q2
        assertEquals(15 * 60_000L, s.state.intervalRemaining(t.now()), "half-time")
    }

    @Test
    fun shotClockSwitchesOffWhenGameClockIsLower() {
        val (s, t) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        s.dispatch(Command.SetClock(30_000))
        s.dispatch(Command.StartClock)
        assertFalse(s.state.shotClockOff(t.now()))
        t.advance(7_000) // game 23 s, shot 17 s
        s.dispatch(Command.ResetShotClock()) // 24 > 23 -> off
        assertTrue(s.state.shotClockOff(t.now()))
        t.advance(23_000)
        val out = s.tick()
        assertTrue(out.cues.none { it.type == CueType.SHOT_CLOCK_EXPIRED }, "no shot horn when off")
        assertTrue(out.cues.any { it.type == CueType.PERIOD_END })
    }

    @Test
    fun tenthsFollowTheRules() {
        val r = Rules.preset(RulePreset.FIBA)
        assertEquals("59.9", ClockFormat.game(59_999, tenths = true, lastMinuteMs = r.tenthsUnderMs))
        assertEquals("!1:00", ClockFormat.game(60_000, tenths = true, lastMinuteMs = r.tenthsUnderMs))
    }

    @Test
    fun overtimeRepeatsUntilThereIsAWinner() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        repeat(3) { s.dispatch(Command.NextPeriod) }
        s.dispatch(Command.EndPeriod)
        assertEquals(GameStatus.PERIOD_BREAK, s.state.status)
        s.dispatch(Command.NextPeriod)
        assertEquals("OT1", ClockFormat.periodShort(s.state.period, s.state.rules))
        assertEquals(300_000, s.state.clock.baseMs)
        s.dispatch(Command.EndPeriod); s.dispatch(Command.NextPeriod)
        assertEquals("OT2", ClockFormat.periodShort(s.state.period, s.state.rules), "still tied: another overtime")
        s.dispatch(Command.AddScore(HOME, 2))
        s.dispatch(Command.EndPeriod); s.dispatch(Command.NextPeriod)
        assertEquals(GameStatus.FINAL, s.state.status)
    }

    // ---- NCAA ----------------------------------------------------------------------------

    @Test
    fun ncaaMenOneAndOneThenDoubleBonusAndTechnicalClasses() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NCAA))
        repeat(6) { s.dispatch(foul(HOME, null)) }
        assertEquals("1 + 1", s.state.bonusText(AWAY))
        repeat(3) { s.dispatch(foul(HOME, null)) }
        assertEquals(PenaltyLevel.DOUBLE_BONUS, s.state.inBonus(AWAY))
        s.dispatch(foul(AWAY, "a0", FoulType.TECHNICAL_2))
        assertEquals(0, s.state.teamFouls(AWAY), "class B: no team foul")
        s.dispatch(foul(AWAY, "a0", FoulType.TECHNICAL_2))
        assertFalse(s.state.player("a0")!!.disqualified(s.state.rules))
        assertTrue(s.dispatch(foul(AWAY, "a0", FoulType.TECHNICAL)).cues.any { it.type == CueType.FOUL_OUT }, "A + B + B ejects")
    }

    @Test
    fun ncaaMenShortAndFullTimeouts() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NCAA))
        assertEquals(4, s.state.timeoutsRemaining(HOME))
        assertEquals(2, s.state.shortTimeoutsRemaining(HOME))
        assertTrue(s.dispatch(Command.StartTimeout(HOME, short = true)).ok)
        assertEquals(30_000, s.state.timeout!!.countdown.baseMs)
        s.dispatch(Command.EndTimeout)
        assertEquals(1, s.state.shortTimeoutsRemaining(HOME))
        assertEquals(4, s.state.timeoutsRemaining(HOME))
        s.dispatch(Command.NextPeriod); s.dispatch(Command.NextPeriod) // tied -> OT
        assertEquals(5, s.state.timeoutsRemaining(HOME), "1 extra per OT plus unused")
    }

    @Test
    fun ncaaWomenBonusPerQuarterAndBenchTechnical() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NCAA_W))
        repeat(3) { s.dispatch(foul(HOME, null)) }
        s.dispatch(foul(HOME, null, FoulType.TECHNICAL)) // bench technical counts
        assertEquals(4, s.state.teamFouls(HOME))
        assertEquals(PenaltyLevel.BONUS, s.state.inBonus(AWAY))
        s.dispatch(Command.NextPeriod)
        assertEquals(PenaltyLevel.NONE, s.state.inBonus(AWAY), "resets each quarter")
        assertEquals(3, s.state.shortTimeoutsRemaining(HOME))
        assertEquals(2, s.state.timeoutsRemaining(HOME))
    }

    // ---- 3x3 -----------------------------------------------------------------------------

    private fun threeByThree(policy: ClockPolicy = ClockPolicy(ClockMode.RUNNING)) = Fixtures.session(Rules.preset(RulePreset.FIBA_3X3), policy = policy)

    @Test
    fun threeByThreeScoringIsOneAndTwo() {
        val (s, _) = threeByThree()
        assertTrue(s.dispatch(Command.AddScore(HOME, 1, shot = ShotType.FIELD_GOAL)).ok)
        assertTrue(s.dispatch(Command.AddScore(HOME, 2, shot = ShotType.ARC)).ok)
        assertFalse(s.dispatch(Command.AddScore(HOME, 3)).ok, "no 3-pointers in classic 3x3")
        assertEquals(3, s.state.team(HOME).score)
    }

    @Test
    fun threeByThreeArcScoringOffersTwoAndThree() {
        val rules = Rules.preset(RulePreset.FIBA_3X3).copy(arcScoring = true).validated()
        val (s, _) = Fixtures.session(rules, policy = ClockPolicy(ClockMode.RUNNING))
        assertEquals(listOf(2, 3), s.state.rules.scoringPoints)
        assertFalse(s.dispatch(Command.AddScore(HOME, 1, shot = ShotType.FIELD_GOAL)).ok, "1 is not a field-goal value in arc mode")
        assertTrue(s.dispatch(Command.AddScore(HOME, 1, shot = ShotType.FREE_THROW)).ok, "free throws still 1")
        assertTrue(s.dispatch(Command.AddScore(HOME, 2)).ok)
        assertTrue(s.dispatch(Command.AddScore(HOME, 3, shot = ShotType.ARC)).ok)
        assertEquals(6, s.state.team(HOME).score)
    }

    @Test
    fun threeByThreeTargetScoreIsEditable() {
        val rules = Rules.preset(RulePreset.FIBA_3X3).copy(targetScore = 11).validated()
        val (s, _) = Fixtures.session(rules, policy = ClockPolicy(ClockMode.RUNNING))
        s.dispatch(Command.StartClock)
        repeat(5) { s.dispatch(Command.AddScore(HOME, 2, shot = ShotType.ARC)) }
        assertFalse(s.state.finalized, "10 is short of 11")
        s.dispatch(Command.AddScore(HOME, 2, shot = ShotType.ARC))
        assertTrue(s.state.finalized, "reaches the custom target")
        assertEquals(12, s.state.team(HOME).score)
    }

    @Test
    fun threeByThreeEndsImmediatelyAt21() {
        val (s, t) = threeByThree()
        s.dispatch(Command.StartClock)
        repeat(9) { s.dispatch(Command.AddScore(AWAY, 2, shot = ShotType.ARC)) } // 18
        t.advance(60_000)
        s.dispatch(Command.AddScore(AWAY, 2, shot = ShotType.ARC)) // 20
        assertFalse(s.state.finalized)
        val out = s.dispatch(Command.AddScore(AWAY, 2, shot = ShotType.ARC)) // 22
        assertTrue(s.state.finalized, "sudden death at 21+")
        assertFalse(s.state.clock.running)
        assertEquals(CueType.GAME_FINAL, out.cues.first().type)
        assertEquals(22, s.state.team(AWAY).score)
        s.dispatch(Command.Undo)
        assertFalse(s.state.finalized, "undo of the winning basket reopens the game")
        assertTrue(s.state.clock.running)
    }

    @Test
    fun threeByThreeFreeThrowCanReach21() {
        val (s, _) = threeByThree()
        repeat(10) { s.dispatch(Command.AddScore(HOME, 2, shot = ShotType.ARC)) } // 20
        s.dispatch(Command.AddFreeThrows(HOME, null, 2, 1))
        assertTrue(s.state.finalized)
        assertEquals(21, s.state.team(HOME).score)
    }

    @Test
    fun threeByThreeOvertimeFirstToScoreTwoPoints() {
        val (s, t) = threeByThree()
        s.dispatch(Command.AddScore(HOME, 2)); s.dispatch(Command.AddScore(AWAY, 2))
        s.dispatch(Command.StartClock)
        t.advance(600_000)
        val buzzer = s.tick()
        assertTrue(buzzer.cues.any { it.type == CueType.PERIOD_END }, "tied: period-end buzzer, not final")
        assertEquals(60_000, s.state.intervalRemaining(t.now()), "1 minute before overtime")
        s.dispatch(Command.NextPeriod)
        assertEquals("OT", ClockFormat.periodShort(s.state.period, s.state.rules))
        assertTrue(s.state.untimed)
        s.dispatch(Command.StartClock)
        t.advance(10 * 60_000) // untimed: no buzzer
        assertTrue(s.tick().cues.none { it.type == CueType.PERIOD_END })
        s.dispatch(Command.AddScore(HOME, 1))
        assertFalse(s.state.finalized, "1 point is not enough")
        s.dispatch(Command.AddScore(AWAY, 1))
        assertFalse(s.state.finalized)
        s.dispatch(Command.AddScore(AWAY, 1, shot = ShotType.FIELD_GOAL))
        assertTrue(s.state.finalized, "away scored 2 points in overtime")
        assertEquals(4, s.state.team(AWAY).score)
    }

    @Test
    fun threeByThreeTwentyOneRuleDoesNotApplyInOvertime() {
        val (s, _) = threeByThree()
        repeat(10) { s.dispatch(Command.AddScore(HOME, 2)); s.dispatch(Command.AddScore(AWAY, 2)) } // 20-20
        s.dispatch(Command.EndPeriod); s.dispatch(Command.NextPeriod)
        s.dispatch(Command.AddScore(HOME, 1)) // 21 in OT
        assertFalse(s.state.finalized, "Interp. 1-1.2: game continues at 21-20 in overtime")
    }

    @Test
    fun threeByThreeTeamFoulsAndPenalties() {
        val (s, _) = threeByThree()
        repeat(5) { s.dispatch(foul(HOME, "h4")) }
        assertEquals(PenaltyLevel.NONE, s.state.inBonus(AWAY))
        s.dispatch(foul(HOME, "h7", FoulType.UNSPORTSMANLIKE)) // counts 2 -> 7
        assertEquals(7, s.state.teamFouls(HOME))
        assertEquals("Bonus", s.state.bonusText(AWAY))
        s.dispatch(foul(HOME, "h7", FoulType.TECHNICAL)) // 8
        s.dispatch(foul(HOME, "h23")) // 9
        assertEquals("2 FT + ball", s.state.bonusText(AWAY), "next (10th) foul: 2 FT + possession")
        repeat(5) { s.dispatch(foul(HOME, "h4")) }
        assertFalse(s.state.player("h4")!!.disqualified(s.state.rules), "no foul-out in 3x3")
        assertTrue(s.dispatch(foul(HOME, "h7", FoulType.UNSPORTSMANLIKE)).cues.any { it.type == CueType.FOUL_OUT }, "2 unsportsmanlike")
        s.dispatch(foul(AWAY, "a0", FoulType.TECHNICAL)); s.dispatch(foul(AWAY, "a0", FoulType.TECHNICAL))
        assertFalse(s.state.player("a0")!!.disqualified(s.state.rules), "2 technicals don't disqualify (Art. 36.2.2)")
        s.dispatch(Command.EndPeriod) // tie 0-0 -> overtime
        s.dispatch(Command.NextPeriod)
        assertTrue(s.state.teamFouls(HOME) >= 10, "team fouls accumulate over the whole game")
    }

    @Test
    fun threeByThreeOneTimeoutCarriedIntoOvertime() {
        val (s, _) = threeByThree()
        assertEquals(1, s.state.timeoutsRemaining(HOME))
        assertTrue(s.dispatch(Command.StartTimeout(HOME)).ok)
        assertEquals(30_000, s.state.timeout!!.countdown.baseMs)
        s.dispatch(Command.EndTimeout)
        assertFalse(s.dispatch(Command.StartTimeout(HOME)).ok)
        s.dispatch(Command.EndPeriod); s.dispatch(Command.NextPeriod)
        assertEquals(0, s.state.timeoutsRemaining(HOME))
        assertEquals(1, s.state.timeoutsRemaining(AWAY), "unused timeout carries into overtime")
    }

    // ---- Legacy games --------------------------------------------------------------------

    @Test
    fun gamesSavedWithLegacyFoulFlagsKeepTheirBehaviour() {
        val legacyFiba2024 = Rules(
            preset = RulePreset.FIBA, technicalCountsAsPersonal = true, technicalsForEjection = 2, flagrantsForEjection = 1,
            unsportsmanlikeForEjection = 2, technicalPlusUnsportsmanlikeEjects = true, technicalCountsAsTeamFoul = true,
            playerFoulLimit = 5, playerFoulWarnAt = 4,
        )
        val (s, _) = Fixtures.session(legacyFiba2024)
        s.dispatch(foul(AWAY, "a11", FoulType.TECHNICAL))
        assertEquals(1, s.state.player("a11")!!.countedFouls(s.state.rules))
        assertEquals(1, s.state.teamFouls(AWAY))
        assertTrue(s.dispatch(foul(AWAY, "a11", FoulType.UNSPORTSMANLIKE)).cues.any { it.type == CueType.FOUL_OUT }, "2024 T + U")
        s.dispatch(foul(HOME, null, FoulType.TECHNICAL))
        assertEquals(0, s.state.teamFouls(HOME), "bench technical was never a team foul")
    }
}
