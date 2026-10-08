package com.watchthetime.domain

import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.TeamSide.AWAY
import com.watchthetime.domain.model.TeamSide.HOME
import com.watchthetime.domain.rules.RulePreset
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.state.PenaltyLevel
import kotlin.test.*

class FoulRulesTest {

    private fun foul(side: com.watchthetime.domain.model.TeamSide, player: String?, type: FoulType = FoulType.PERSONAL) =
        Command.AddFoul(side, player, type)

    @Test
    fun nbaWarnsAtFiveAndFoulsOutAtSix() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        repeat(4) { assertTrue(s.dispatch(foul(HOME, "h4")).cues.none { it.type == CueType.FOUL_WARNING }) }
        val fifth = s.dispatch(foul(HOME, "h4"))
        assertTrue(fifth.cues.any { it.type == CueType.FOUL_WARNING && it.playerId == "h4" })
        assertTrue(s.state.player("h4")!!.inFoulTrouble(s.state.rules))
        val sixth = s.dispatch(foul(HOME, "h4"))
        assertEquals(CueType.FOUL_OUT, sixth.cues.first().type, "foul-out is highest priority cue")
        assertTrue(s.state.player("h4")!!.disqualified(s.state.rules))
    }

    @Test
    fun configurableFoulLimit() {
        val (s, _) = Fixtures.session(Rules(playerFoulLimit = 3, playerFoulWarnAt = 2))
        s.dispatch(foul(AWAY, "a0"))
        assertTrue(s.dispatch(foul(AWAY, "a0")).cues.any { it.type == CueType.FOUL_WARNING })
        assertTrue(s.dispatch(foul(AWAY, "a0")).cues.any { it.type == CueType.FOUL_OUT })
    }

    @Test
    fun nbaTechnicalsAreFlagsNotPersonalsAndTwoEject() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        val first = s.dispatch(foul(HOME, "h7", FoulType.TECHNICAL))
        assertTrue(first.cues.any { it.type == CueType.FOUL_FLAG })
        val p = s.state.player("h7")!!
        assertEquals(0, p.countedFouls(s.state.rules))
        assertEquals(listOf(FoulType.TECHNICAL to 1), p.flagBadges())
        assertEquals(0, s.state.teamFouls(HOME), "NBA technicals are not team fouls")
        assertTrue(s.dispatch(foul(HOME, "h7", FoulType.TECHNICAL)).cues.any { it.type == CueType.FOUL_OUT })
    }

    @Test
    fun fibaTechnicalPlusUnsportsmanlikeDisqualifies() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        s.dispatch(foul(AWAY, "a11", FoulType.TECHNICAL))
        assertEquals(1, s.state.player("a11")!!.countedFouls(s.state.rules), "FIBA: T counts as a foul")
        assertEquals(1, s.state.teamFouls(AWAY))
        assertTrue(s.dispatch(foul(AWAY, "a11", FoulType.UNSPORTSMANLIKE)).cues.any { it.type == CueType.FOUL_OUT })
    }

    @Test
    fun benchTechnicalDoesNotCountAsTeamFoul() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        s.dispatch(foul(HOME, null, FoulType.TECHNICAL))
        assertEquals(0, s.state.teamFouls(HOME))
    }

    @Test
    fun nbaBonusAfterFourTeamFoulsPerQuarterAndResets() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        repeat(3) { s.dispatch(foul(HOME, "h23")) }
        assertEquals(PenaltyLevel.NONE, s.state.penalty(HOME))
        val fourth = s.dispatch(foul(HOME, "h4"))
        assertTrue(fourth.cues.any { it.type == CueType.BONUS && it.side == AWAY }, "away shoots on the next home foul")
        assertEquals(PenaltyLevel.BONUS, s.state.inBonus(AWAY))
        assertEquals(PenaltyLevel.NONE, s.state.inBonus(HOME))
        s.dispatch(Command.NextPeriod)
        assertEquals(0, s.state.teamFouls(HOME), "team fouls reset each quarter")
        assertEquals(4, s.state.teamFouls(HOME, period = 1))
    }

    @Test
    fun nbaLastTwoMinutesRule() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        s.dispatch(foul(AWAY, "a0"))
        s.dispatch(Command.SetClock(110_000))
        assertEquals(PenaltyLevel.NONE, s.state.penalty(AWAY))
        val late = s.dispatch(foul(AWAY, "a0"))
        assertEquals(PenaltyLevel.BONUS, s.state.penalty(AWAY), "second foul in last 2:00 shoots")
        assertTrue(late.cues.any { it.type == CueType.BONUS })
    }

    @Test
    fun nbaOvertimeThresholdIsFour() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA).copy(periodLengthMs = 60_000))
        repeat(4) { s.dispatch(Command.NextPeriod) } // tied -> OT1
        assertTrue(s.state.rules.isOvertime(s.state.period))
        repeat(2) { s.dispatch(foul(HOME, "h4")) }
        assertEquals(PenaltyLevel.NONE, s.state.penalty(HOME))
        s.dispatch(foul(HOME, "h7"))
        assertEquals(PenaltyLevel.BONUS, s.state.penalty(HOME))
    }

    @Test
    fun fibaOvertimeContinuesFourthQuarterCount() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        repeat(3) { s.dispatch(Command.NextPeriod) }
        repeat(4) { s.dispatch(foul(AWAY, "a00")) }
        s.dispatch(Command.NextPeriod) // tied -> OT
        assertEquals(5, s.state.period)
        assertEquals(4, s.state.teamFouls(AWAY))
        assertEquals(PenaltyLevel.BONUS, s.state.penalty(AWAY))
    }

    @Test
    fun ncaaDoubleBonus() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NCAA).copy(playerFoulLimit = 20, playerFoulWarnAt = 19))
        repeat(6) { s.dispatch(foul(HOME, "h4")) }
        assertEquals(PenaltyLevel.BONUS, s.state.penalty(HOME), "1-and-1 on the 7th")
        repeat(3) { s.dispatch(foul(HOME, "h4")) }
        assertEquals(PenaltyLevel.DOUBLE_BONUS, s.state.penalty(HOME), "double bonus on the 10th")
    }

    @Test
    fun timeoutAllocationsPerPreset() {
        val (nba, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        assertEquals(7, nba.state.timeoutsRemaining(HOME))
        nba.dispatch(Command.StartTimeout(HOME)); nba.dispatch(Command.EndTimeout)
        repeat(2) { nba.dispatch(Command.NextPeriod) }
        assertEquals(6, nba.state.timeoutsRemaining(HOME), "NBA timeouts carry through the game")

        val (fiba, _) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        fiba.dispatch(Command.StartTimeout(AWAY)); fiba.dispatch(Command.EndTimeout)
        fiba.dispatch(Command.StartTimeout(AWAY)); fiba.dispatch(Command.EndTimeout)
        val third = fiba.dispatch(Command.StartTimeout(AWAY))
        assertFalse(third.ok)
        assertEquals(CueType.ERROR, third.cues.single().type)
        repeat(2) { fiba.dispatch(Command.NextPeriod) }
        assertEquals(3, fiba.state.timeoutsRemaining(AWAY), "FIBA second half = 3, no carry")
        fiba.dispatch(Command.AdjustTimeouts(AWAY, -1))
        assertEquals(2, fiba.state.timeoutsRemaining(AWAY))
    }
}
