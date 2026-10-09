package com.watchthetime.domain.rules

import com.watchthetime.domain.model.FoulType.*
import com.watchthetime.domain.rules.Rules.Companion.MIN
import com.watchthetime.domain.rules.Rules.Companion.SEC

/**
 * Official presets. Every value is cited in RULES.md (article/rule numbers and source URLs).
 * Where a rulebook leaves something open, the choice made here is listed under
 * "Uncertain / not modelled" in RULES.md and is editable in the Custom preset.
 */
internal object Presets {

    /** FIBA Official Basketball Rules 2026 (valid from 1 Oct 2026). */
    val fiba = Rules(
        preset = RulePreset.FIBA,
        kind = GameKind.FIVE_ON_FIVE,
        format = PeriodFormat.QUARTERS,
        periodLengthMs = 10 * MIN,                 // Art. 8.1
        overtimeLengthMs = 5 * MIN,                // Art. 8.7
        intervalMs = 2 * MIN,                      // Art. 8.3
        halftimeMs = 15 * MIN,                     // Art. 8.4
        overtimeIntervalMs = 2 * MIN,              // Art. 8.3
        playerFoulLimit = 5, playerFoulWarnAt = 4, // Art. 41.1
        foulTable = listOf(
            FoulRule(PERSONAL, "Personal", countsPersonal = true, teamFouls = 1),
            FoulRule(OFFENSIVE, "Offensive", countsPersonal = true, teamFouls = 1),                                // 42.2.2: throw-in only
            FoulRule(TECHNICAL, "Technical (cat. 1)", true, teamFouls = 1, benchTeamFouls = 0, freeThrows = 1),    // 36.3.1-2
            FoulRule(TECHNICAL_2, "Technical (cat. 2)", true, teamFouls = 1, benchTeamFouls = 0, freeThrows = 1),  // 36.2.1, 36.3.1
            FoulRule(DISRUPTIVE, "Disruptive", true, teamFouls = 1, freeThrows = 2, possession = true),            // 37.2.2
            FoulRule(FLAGRANT, "Flagrant", true, teamFouls = 1, freeThrows = 2, possession = true),                // 38.2.2
            FoulRule(DISQUALIFYING, "Disqualifying", true, teamFouls = 1, freeThrows = 2, possession = true),      // 39.3
        ),
        ejections = listOf(
            EjectionRule(mapOf(TECHNICAL to 1, FLAGRANT to 1), 2, "2 cat. 1 technicals / flagrants"),             // 36.2.3, 38.2.3
            EjectionRule(mapOf(DISQUALIFYING to 1), 1, "Disqualifying foul"),                                      // 39.3.2
        ),
        teamFoulScope = TeamFoulScope.PERIOD,
        bonusAt = 5, overtimeBonusAt = 5, overtimeFoulsCarry = true,                                              // 42.1.1, 42.1.3
        lastTwoMinutesRule = false, doubleBonusAt = 0, bonusFreeThrows = 2,                                       // 42.2.1
        timeoutLengthMs = 60 * SEC,                                                                               // 18.2.1
        timeoutsFirstHalf = 2, timeoutsSecondHalf = 3, timeoutsPerOvertime = 1,                                   // 18.2.5
        timeoutsCarryToSecondHalf = false, timeoutsCarryIntoOvertime = false,                                     // 18.2.6
        timeoutCaps = listOf(TimeoutCap(2 * MIN, 2)),                                                              // 18.2.5
        timeoutWarningMs = 10 * SEC,                                                                              // 50.3: signal at 50 s
        shotClockEnabled = true, shotClockFullMs = 24 * SEC, shotClockShortMs = 14 * SEC,                         // 29.1.1, 29.2
        shotClockOffWhenLower = true,                                                                             // 51.5 / Interp. 29/51-52
        tenthsUnderMs = 60 * SEC, lastMinuteWarningMs = 60 * SEC,                                                 // Equipment 3.3
        scoreStopFinalMs = 2 * MIN, scoreStopOtherMs = 0,                                                         // 50.2
    )

    /** NBA Official Rules 2026-27. */
    val nba = Rules(
        preset = RulePreset.NBA,
        kind = GameKind.FIVE_ON_FIVE,
        format = PeriodFormat.QUARTERS,
        periodLengthMs = 12 * MIN, overtimeLengthMs = 5 * MIN,                                                    // Rule 5-II
        intervalMs = 150 * SEC, halftimeMs = 15 * MIN, overtimeIntervalMs = 150 * SEC,                           // Rule 5-II(c)(d), local games
        playerFoulLimit = 6, playerFoulWarnAt = 5,                                                               // Rule 3-I(a)
        foulTable = listOf(
            FoulRule(PERSONAL, "Personal", countsPersonal = true, teamFouls = 1),
            FoulRule(OFFENSIVE, "Offensive", countsPersonal = true, teamFouls = 0),                                // 12B-VII: no team foul
            FoulRule(TECHNICAL, "Technical (unsportsmanlike)", false, teamFouls = 0, benchTeamFouls = 0, freeThrows = 1), // 12A-V
            FoulRule(TECHNICAL_2, "Technical (other)", false, teamFouls = 0, benchTeamFouls = 0, freeThrows = 1),  // 12A-V(c)
            FoulRule(FLAGRANT, "Flagrant 1", true, teamFouls = 1, freeThrows = 2, possession = true),              // 12B-IV(a)
            FoulRule(FLAGRANT_2, "Flagrant 2", true, teamFouls = 1, freeThrows = 2, possession = true),            // 12B-IV(b)
        ),
        ejections = listOf(
            EjectionRule(mapOf(TECHNICAL to 1), 2, "2 unsportsmanlike technicals"),                                // 12A-V(b)
            EjectionRule(mapOf(FLAGRANT to 1), 2, "2 flagrant 1"),                                                 // 12B-IV(a)(5)
            EjectionRule(mapOf(FLAGRANT_2 to 1), 1, "Flagrant 2"),                                                 // 12B-IV(b)(5)
        ),
        teamFoulScope = TeamFoulScope.PERIOD,
        bonusAt = 5, overtimeBonusAt = 4, overtimeFoulsCarry = false, lastTwoMinutesRule = true,                  // 12B-V(a)
        doubleBonusAt = 0, bonusFreeThrows = 2,
        timeoutLengthMs = 75 * SEC,                                                                               // 5-VI(c), non-mandatory
        timeoutsFirstHalf = 7, timeoutsSecondHalf = 0, timeoutsPerOvertime = 2,                                   // 5-VI(a)(b)
        timeoutsCarryToSecondHalf = true, timeoutsCarryIntoOvertime = false,                                      // carry into OT: not stated (RULES.md)
        timeoutCaps = listOf(TimeoutCap(12 * MIN, 4), TimeoutCap(3 * MIN, 2)),                                    // 5-VI(a)
        shotClockEnabled = true, shotClockFullMs = 24 * SEC, shotClockShortMs = 14 * SEC,                         // Rule 7
        shotClockOffWhenLower = true,                                                                             // 7-II(i)
        tenthsUnderMs = 60 * SEC, lastMinuteWarningMs = 60 * SEC,                                                 // 5-II(h)
        scoreStopFinalMs = 2 * MIN, scoreStopOtherMs = 1 * MIN,                                                   // 5-V(b)
    )

    /** NCAA Men's Basketball Rules 2026-27 (non-media timeouts). */
    val ncaaMen = Rules(
        preset = RulePreset.NCAA,
        kind = GameKind.FIVE_ON_FIVE,
        format = PeriodFormat.HALVES,
        periodLengthMs = 20 * MIN, overtimeLengthMs = 5 * MIN,                                                    // 5-6.1
        intervalMs = 0, halftimeMs = 15 * MIN, overtimeIntervalMs = 1 * MIN,                                      // 5-6.1
        playerFoulLimit = 5, playerFoulWarnAt = 4,                                                               // 4-12.1
        foulTable = listOf(
            FoulRule(PERSONAL, "Personal", countsPersonal = true, teamFouls = 1),
            FoulRule(OFFENSIVE, "Player-control", countsPersonal = true, teamFouls = 1),                            // 8-2.1 exception: no FT
            FoulRule(TECHNICAL, "Class A technical", true, teamFouls = 1, benchTeamFouls = 1, freeThrows = 2),     // 10-3, 8-2.3
            FoulRule(TECHNICAL_2, "Class B technical", false, teamFouls = 0, benchTeamFouls = 0, freeThrows = 1),  // 10-4
            FoulRule(FLAGRANT, "Flagrant 1", true, teamFouls = 1, freeThrows = 2, possession = true),              // 4-15.2, 10-1
            FoulRule(FLAGRANT_2, "Flagrant 2", true, teamFouls = 1, freeThrows = 2, possession = true),            // 4-15.2.c
        ),
        ejections = listOf(
            EjectionRule(mapOf(TECHNICAL to 3, TECHNICAL_2 to 2), 6, "AA / ABB / BBB technicals"),                // 10-3, 10-4
            EjectionRule(mapOf(FLAGRANT_2 to 1), 1, "Flagrant 2"),                                                 // 4-15.2.c
            EjectionRule(mapOf(FLAGRANT to 1), 3, "3 flagrant 1"),                                                 // 4-12.1
        ),
        teamFoulScope = TeamFoulScope.PERIOD,
        bonusAt = 7, doubleBonusAt = 10, oneAndOne = true, bonusFreeThrows = 2,                                    // 8-2.1, 8-2.2
        overtimeBonusAt = 7, overtimeFoulsCarry = true, lastTwoMinutesRule = false,                               // 5-9.4
        timeoutLengthMs = 75 * SEC,                                                                               // 5-14.4
        timeoutsFirstHalf = 4, timeoutsSecondHalf = 0, timeoutsPerOvertime = 1,                                   // 5-14.8
        timeoutsCarryToSecondHalf = true, timeoutsCarryIntoOvertime = true,                                       // 5-14.8
        shortTimeoutsPerGame = 2, shortTimeoutsPerOvertime = 0, shortTimeoutLengthMs = 30 * SEC,                 // 5-14.8
        shotClockEnabled = true, shotClockFullMs = 30 * SEC, shotClockShortMs = 20 * SEC,                         // 2-11
        shotClockOffWhenLower = true,                                                                             // 2-11.2
        tenthsUnderMs = 60 * SEC, lastMinuteWarningMs = 60 * SEC,                                                 // 1-18.2
        scoreStopFinalMs = 59_900, scoreStopOtherMs = 0,                                                          // 5-11.9
    )

    /** NCAA Women's Basketball Rules 2025-26 and 2026-27 (non-media timeouts). */
    val ncaaWomen = Rules(
        preset = RulePreset.NCAA_W,
        kind = GameKind.FIVE_ON_FIVE,
        format = PeriodFormat.QUARTERS,
        periodLengthMs = 10 * MIN, overtimeLengthMs = 5 * MIN,                                                    // 5-6.1
        intervalMs = 75 * SEC, halftimeMs = 15 * MIN, overtimeIntervalMs = 1 * MIN,                              // 5-6.1
        playerFoulLimit = 5, playerFoulWarnAt = 4,                                                               // 4-12.1
        foulTable = listOf(
            FoulRule(PERSONAL, "Personal", countsPersonal = true, teamFouls = 1),
            FoulRule(OFFENSIVE, "Offensive", countsPersonal = true, teamFouls = 1),                                 // 8-2.1: no FT
            FoulRule(TECHNICAL, "Technical", true, teamFouls = 1, benchTeamFouls = 1, freeThrows = 2, possession = true), // 8-2.2, 10-12
            FoulRule(TECHNICAL_2, "Administrative technical", false, teamFouls = 0, benchTeamFouls = 0, freeThrows = 2),  // 10-12.2
            FoulRule(FLAGRANT, "Flagrant 1", true, teamFouls = 1, freeThrows = 2, possession = true),              // 10-13
            FoulRule(FLAGRANT_2, "Flagrant 2", true, teamFouls = 1, freeThrows = 2, possession = true),            // 10-14
        ),
        ejections = listOf(
            EjectionRule(mapOf(FLAGRANT_2 to 1), 1, "Flagrant 2"),                                                 // 4-14.2
            EjectionRule(mapOf(TECHNICAL to 1, FLAGRANT to 1), 2, "2 technicals / flagrant 1"),                    // 4-14.2, 10-12.4
        ),
        teamFoulScope = TeamFoulScope.PERIOD,
        bonusAt = 5, doubleBonusAt = 0, bonusFreeThrows = 2, oneAndOne = false,                                    // 8-2.1
        overtimeBonusAt = 5, overtimeFoulsCarry = true, lastTwoMinutesRule = false,                               // 5-9.4 (inferred)
        timeoutLengthMs = 60 * SEC,                                                                               // 5-14.5
        timeoutsFirstHalf = 2, timeoutsSecondHalf = 0, timeoutsPerOvertime = 0,                                   // 5-14.9
        timeoutsCarryToSecondHalf = true, timeoutsCarryIntoOvertime = true,
        shortTimeoutsPerGame = 3, shortTimeoutsPerOvertime = 1, shortTimeoutLengthMs = 30 * SEC,                 // 5-14.9
        shotClockEnabled = true, shotClockFullMs = 30 * SEC, shotClockShortMs = 20 * SEC,                         // 2-11
        shotClockOffWhenLower = true,                                                                             // 2-11.2
        tenthsUnderMs = 60 * SEC, lastMinuteWarningMs = 60 * SEC,                                                 // 1-18.2
        scoreStopFinalMs = 59_900, scoreStopOtherMs = 0,                                                          // 5-11.9
    )

    /** FIBA 3x3 Basketball Rules (valid as of 1 January 2026). */
    val fiba3x3 = Rules(
        preset = RulePreset.FIBA_3X3,
        kind = GameKind.THREE_X_THREE,
        format = PeriodFormat.SINGLE,
        periodLengthMs = 10 * MIN,                                                                                // Art. 8.1
        overtimeLengthMs = 0,                                                                                     // Art. 8.5: first to 2, untimed
        intervalMs = 0, halftimeMs = 0, overtimeIntervalMs = 1 * MIN,                                             // Art. 8.2
        targetScore = 21,                                                                                         // Art. 1.2, 9.5
        overtimeWinPoints = 2,                                                                                    // Art. 8.5, 9.6
        playerFoulLimit = 0, playerFoulWarnAt = 0,                                                               // Art. 41.1.3: no foul-out
        foulTable = listOf(
            FoulRule(PERSONAL, "Personal", countsPersonal = true, teamFouls = 1),
            FoulRule(OFFENSIVE, "Offensive", countsPersonal = true, teamFouls = 1),                                 // 41.2.2: check-ball only
            FoulRule(TECHNICAL, "Technical", false, teamFouls = 1, benchTeamFouls = 1, freeThrows = 1),            // 36.3, 36.3.1
            FoulRule(UNSPORTSMANLIKE, "Unsportsmanlike", true, teamFouls = 2, freeThrows = 2),                     // 37.2.2, 37.2.4
            FoulRule(DISQUALIFYING, "Disqualifying", true, teamFouls = 2, freeThrows = 2, possession = true),      // 38.3.3-4
        ),
        ejections = listOf(
            EjectionRule(mapOf(UNSPORTSMANLIKE to 1), 2, "2 unsportsmanlike"),                                     // 37.2.5
            EjectionRule(mapOf(DISQUALIFYING to 1), 1, "Disqualifying foul"),                                      // 38
        ),
        teamFoulScope = TeamFoulScope.GAME,                                                                       // no reset (RULES.md)
        bonusAt = 7, doubleBonusAt = 10, bonusFreeThrows = 2,                                                     // 41.1.1, 41.2.1
        overtimeBonusAt = 7, overtimeFoulsCarry = true, lastTwoMinutesRule = false,
        timeoutLengthMs = 30 * SEC,                                                                               // 18.2.3
        timeoutsFirstHalf = 0, timeoutsSecondHalf = 1, timeoutsPerOvertime = 0,                                   // 18.2.1 (single period = "2nd half" slot)
        timeoutsCarryToSecondHalf = false, timeoutsCarryIntoOvertime = true,                                      // 18.2.7
        timeoutWarningMs = 10 * SEC,                                                                              // 49.3: signal after 20 s
        shotClockEnabled = true, shotClockFullMs = 12 * SEC, shotClockShortMs = 12 * SEC,                         // 29.1.1
        shotClockOffWhenLower = true,                                                                             // 50.4
        tenthsUnderMs = 60 * SEC, lastMinuteWarningMs = 60 * SEC,                                                 // not in 3x3 rules: FIBA equipment default
        scoreStopFinalMs = 0, scoreStopOtherMs = 0,                                                               // ball stays live after a basket
    )
}
