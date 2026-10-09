package com.watchthetime.domain.rules

import kotlinx.serialization.Serializable

@Serializable
enum class PeriodFormat(val regulationPeriods: Int) {
    QUARTERS(4),
    HALVES(2),
}

@Serializable
enum class RulePreset(val label: String) {
    NBA("NBA"),
    FIBA("FIBA"),
    NCAA("NCAA (men)"),
    NFHS("NFHS (US high school)"),
    CUSTOM("Custom"),
}

/**
 * Every rule that affects how events are interpreted. Rules are themselves event-sourced
 * ([com.watchthetime.domain.event.RulesUpdated]) so changing them mid-game recomputes totals.
 */
@Serializable
data class Rules(
    val preset: RulePreset = RulePreset.NBA,
    val format: PeriodFormat = PeriodFormat.QUARTERS,
    val periodLengthMs: Long = 12 * MIN,
    val overtimeLengthMs: Long = 5 * MIN,

    // --- Player fouls -------------------------------------------------------------------
    /** Player is disqualified when counted fouls reach this number. */
    val playerFoulLimit: Int = 6,
    /** Warning shown at this many counted fouls (defaults to one before the limit). */
    val playerFoulWarnAt: Int = 5,
    val technicalCountsAsPersonal: Boolean = false,
    /** Ejection thresholds for flags (0 = never). */
    val technicalsForEjection: Int = 2,
    val flagrantsForEjection: Int = 2,
    val unsportsmanlikeForEjection: Int = 0,
    /** FIBA: one technical plus one unsportsmanlike disqualifies. */
    val technicalPlusUnsportsmanlikeEjects: Boolean = false,

    // --- Team fouls / bonus ---------------------------------------------------------------
    /** The Nth team foul in a period puts the opponent in the bonus (free throws). */
    val bonusAt: Int = 5,
    /** Optional double bonus (NCAA 10). 0 = disabled. */
    val doubleBonusAt: Int = 0,
    /** Bonus threshold in each overtime period (NBA 4). */
    val overtimeBonusAt: Int = 4,
    /** If true, overtime periods continue the team-foul count of the last regulation period. */
    val overtimeFoulsCarry: Boolean = false,
    /** NBA: penalty from the 2nd team foul in the last 2:00 of any period. */
    val lastTwoMinutesRule: Boolean = true,
    val technicalCountsAsTeamFoul: Boolean = false,

    // --- Timeouts -------------------------------------------------------------------------
    val timeoutLengthMs: Long = 75 * SEC,
    val timeoutsFirstHalf: Int = 7,
    val timeoutsSecondHalf: Int = 0,
    val timeoutsPerOvertime: Int = 2,
    val timeoutsCarryToSecondHalf: Boolean = true,
    val timeoutsCarryIntoOvertime: Boolean = false,

    // --- Shot clock -----------------------------------------------------------------------
    val shotClockEnabled: Boolean = true,
    val shotClockFullMs: Long = 24 * SEC,
    val shotClockShortMs: Long = 14 * SEC,

    // --- Display --------------------------------------------------------------------------
    val lastMinuteWarningMs: Long = 60 * SEC,

    // --- Late-game clock stops after a made basket -------------------------------------------
    /** Last regulation period and every overtime: the clock stops on a score at or below this. 0 = never. */
    val scoreStopFinalMs: Long = 2 * MIN,
    /** Any other period. 0 = never. */
    val scoreStopOtherMs: Long = 1 * MIN,
) {
    val regulationPeriods: Int get() = format.regulationPeriods
    val halfPeriod: Int get() = regulationPeriods / 2

    /** True when [clockMs] in [period] is inside the official "clock stops after a score" window. */
    fun inLateWindow(period: Int, clockMs: Long): Boolean {
        val w = if (period >= regulationPeriods) scoreStopFinalMs else scoreStopOtherMs
        return w > 0 && clockMs <= w
    }

    fun isOvertime(period: Int) = period > regulationPeriods
    fun lengthOf(period: Int) = if (isOvertime(period)) overtimeLengthMs else periodLengthMs

    fun validated(): Rules = copy(
        periodLengthMs = periodLengthMs.coerceIn(1 * MIN, 60 * MIN),
        overtimeLengthMs = overtimeLengthMs.coerceIn(1 * MIN, 20 * MIN),
        playerFoulLimit = playerFoulLimit.coerceIn(1, 20),
        playerFoulWarnAt = playerFoulWarnAt.coerceIn(0, playerFoulLimit.coerceIn(1, 20)),
        bonusAt = bonusAt.coerceIn(1, 30),
        overtimeBonusAt = overtimeBonusAt.coerceIn(1, 30),
        timeoutLengthMs = timeoutLengthMs.coerceIn(10 * SEC, 5 * MIN),
        shotClockFullMs = shotClockFullMs.coerceIn(5 * SEC, 60 * SEC),
        shotClockShortMs = shotClockShortMs.coerceIn(5 * SEC, shotClockFullMs.coerceIn(5 * SEC, 60 * SEC)),
    )

    companion object {
        const val SEC = 1_000L
        const val MIN = 60_000L

        /** Selectable period lengths offered in settings UIs. */
        val PERIOD_LENGTH_CHOICES_MIN = listOf(5, 6, 7, 8, 10, 12, 15, 16, 20)
        val OVERTIME_LENGTH_CHOICES_MIN = listOf(2, 3, 4, 5)

        fun preset(p: RulePreset): Rules = when (p) {
            RulePreset.NBA, RulePreset.CUSTOM -> Rules(preset = p)
            RulePreset.FIBA -> Rules(
                preset = p,
                format = PeriodFormat.QUARTERS,
                periodLengthMs = 10 * MIN,
                overtimeLengthMs = 5 * MIN,
                playerFoulLimit = 5,
                playerFoulWarnAt = 4,
                technicalCountsAsPersonal = true,
                technicalsForEjection = 2,
                flagrantsForEjection = 1, // FIBA "disqualifying foul"
                unsportsmanlikeForEjection = 2,
                technicalPlusUnsportsmanlikeEjects = true,
                bonusAt = 5,
                overtimeBonusAt = 5,
                overtimeFoulsCarry = true,
                lastTwoMinutesRule = false,
                technicalCountsAsTeamFoul = true,
                timeoutLengthMs = 60 * SEC,
                timeoutsFirstHalf = 2,
                timeoutsSecondHalf = 3,
                timeoutsPerOvertime = 1,
                timeoutsCarryToSecondHalf = false,
                timeoutsCarryIntoOvertime = false,
                shotClockFullMs = 24 * SEC,
                shotClockShortMs = 14 * SEC,
                scoreStopFinalMs = 2 * MIN,
                scoreStopOtherMs = 0,
            )
            RulePreset.NCAA -> Rules(
                preset = p,
                format = PeriodFormat.HALVES,
                periodLengthMs = 20 * MIN,
                overtimeLengthMs = 5 * MIN,
                playerFoulLimit = 5,
                playerFoulWarnAt = 4,
                technicalCountsAsPersonal = true,
                technicalsForEjection = 2,
                flagrantsForEjection = 1,
                unsportsmanlikeForEjection = 0,
                bonusAt = 7,
                doubleBonusAt = 10,
                overtimeBonusAt = 7,
                overtimeFoulsCarry = true,
                lastTwoMinutesRule = false,
                technicalCountsAsTeamFoul = true,
                timeoutLengthMs = 60 * SEC,
                timeoutsFirstHalf = 4,
                timeoutsSecondHalf = 0,
                timeoutsPerOvertime = 1,
                timeoutsCarryToSecondHalf = true,
                timeoutsCarryIntoOvertime = true,
                shotClockFullMs = 30 * SEC,
                shotClockShortMs = 20 * SEC,
                scoreStopFinalMs = 59_900,
                scoreStopOtherMs = 0,
            )
            RulePreset.NFHS -> Rules(
                preset = p,
                format = PeriodFormat.QUARTERS,
                periodLengthMs = 8 * MIN,
                overtimeLengthMs = 4 * MIN,
                playerFoulLimit = 5,
                playerFoulWarnAt = 4,
                technicalCountsAsPersonal = true,
                technicalsForEjection = 2,
                flagrantsForEjection = 1,
                unsportsmanlikeForEjection = 0,
                bonusAt = 5,
                overtimeBonusAt = 5,
                overtimeFoulsCarry = true,
                lastTwoMinutesRule = false,
                technicalCountsAsTeamFoul = true,
                timeoutLengthMs = 60 * SEC,
                timeoutsFirstHalf = 5,
                timeoutsSecondHalf = 0,
                timeoutsPerOvertime = 1,
                timeoutsCarryToSecondHalf = true,
                timeoutsCarryIntoOvertime = true,
                shotClockEnabled = false,
                shotClockFullMs = 35 * SEC,
                shotClockShortMs = 20 * SEC,
            )
        }
    }
}
