package com.watchthetime.domain.rules

import com.watchthetime.domain.model.FoulType
import kotlinx.serialization.Serializable

@Serializable
enum class PeriodFormat(val regulationPeriods: Int) {
    QUARTERS(4),
    HALVES(2),
    /** One period (FIBA 3x3). */
    SINGLE(1),
}

@Serializable
enum class GameKind(val label: String) {
    FIVE_ON_FIVE("5-on-5"),
    THREE_X_THREE("3x3"),
}

/**
 * Rule presets. Sources for every value: RULES.md. [selectable] = offered for new games;
 * NFHS stays only so games created with it before still load.
 */
@Serializable
enum class RulePreset(val label: String, val selectable: Boolean = true) {
    NBA("NBA"),
    FIBA("FIBA 2026"),
    NCAA("NCAA men"),
    NCAA_W("NCAA women"),
    FIBA_3X3("FIBA 3x3"),
    CUSTOM("Custom"),
    NFHS("NFHS (legacy)", selectable = false),
}

/** Team-foul counting window. PERIOD = each quarter/half (overtime per [Rules.overtimeFoulsCarry]); GAME = whole game (3x3). */
@Serializable
enum class TeamFoulScope { PERIOD, GAME }

/**
 * How one foul type is treated by a rule set.
 * @property countsPersonal counts toward the player foul limit (foul-out)
 * @property teamFouls team-foul weight when charged to a player (0 = not a team foul; 3x3 unsportsmanlike = 2)
 * @property benchTeamFouls weight when charged to the team/bench/coach (no player)
 * @property freeThrows free throws this foul type awards by itself (0 = only via shooting fouls or the team-foul penalty)
 * @property possession the fouled team also gets the ball
 */
@Serializable
data class FoulRule(
    val type: FoulType,
    val label: String,
    val countsPersonal: Boolean,
    val teamFouls: Int,
    val benchTeamFouls: Int = teamFouls,
    val freeThrows: Int = 0,
    val possession: Boolean = false,
)

/** A player is ejected/disqualified when Σ count(type) × weight ≥ [threshold]. */
@Serializable
data class EjectionRule(val weights: Map<FoulType, Int>, val threshold: Int, val label: String)

/** At most [max] timeouts in the last regulation period at or below [underMs] on the game clock. */
@Serializable
data class TimeoutCap(val underMs: Long, val max: Int)

/**
 * Every rule that affects how events are interpreted. Rules are themselves event-sourced
 * ([com.watchthetime.domain.event.RulesUpdated]) so changing them mid-game recomputes totals.
 *
 * Defaults reproduce the behaviour of games saved before the rules engine was extended
 * (NBA-like, no intervals, foul table derived from the legacy flags). New games always use a
 * [preset].
 */
@Serializable
data class Rules(
    val preset: RulePreset = RulePreset.NBA,
    val kind: GameKind = GameKind.FIVE_ON_FIVE,
    val format: PeriodFormat = PeriodFormat.QUARTERS,
    val periodLengthMs: Long = 12 * MIN,
    /** 0 = untimed overtime (3x3: first to score 2 points). */
    val overtimeLengthMs: Long = 5 * MIN,

    // --- Intervals (0 = no interval timer) ------------------------------------------------
    val intervalMs: Long = 0,
    val halftimeMs: Long = 0,
    val overtimeIntervalMs: Long = 0,

    // --- Winning --------------------------------------------------------------------------
    /** Regulation ends as soon as a team reaches this score (3x3: 21). 0 = off. */
    val targetScore: Int = 0,
    /** Overtime ends as soon as a team has scored this many points in it (3x3: 2). 0 = off. */
    val overtimeWinPoints: Int = 0,
    /**
     * 3x3 scoring style. false = official 1 & 2 (inside / outside the arc);
     * true = arcade-style 2 & 3 (inside / behind the arc). Free throws are always 1.
     */
    val arcScoring: Boolean = false,

    // --- Player fouls -------------------------------------------------------------------
    /** Player is disqualified when counted fouls reach this number. 0 = no foul-out (3x3). */
    val playerFoulLimit: Int = 6,
    /** Warning shown at this many counted fouls. */
    val playerFoulWarnAt: Int = 5,
    /** Per-type treatment. Null only in games saved before the table existed: see [validated]. */
    val foulTable: List<FoulRule>? = null,
    val ejections: List<EjectionRule>? = null,

    // Legacy foul flags (pre foul table). Kept so old games decode; converted in [validated].
    val technicalCountsAsPersonal: Boolean = false,
    val technicalsForEjection: Int = 2,
    val flagrantsForEjection: Int = 2,
    val unsportsmanlikeForEjection: Int = 0,
    val technicalPlusUnsportsmanlikeEjects: Boolean = false,
    val technicalCountsAsTeamFoul: Boolean = false,

    // --- Team fouls / penalty ---------------------------------------------------------------
    val teamFoulScope: TeamFoulScope = TeamFoulScope.PERIOD,
    /** The Nth team foul (in the scope) is the first one penalised with free throws. */
    val bonusAt: Int = 5,
    /** Second penalty level (NCAA men: 2 shots from the 10th; 3x3: 2 FT + possession from the 10th). 0 = off. */
    val doubleBonusAt: Int = 0,
    /** Penalty threshold in each overtime period when overtime fouls do not carry (NBA 4). */
    val overtimeBonusAt: Int = 4,
    /** Overtime periods continue the team-foul count of the last regulation period. */
    val overtimeFoulsCarry: Boolean = false,
    /** NBA: penalty from the 2nd team foul in the last 2:00 of a period. */
    val lastTwoMinutesRule: Boolean = true,
    /** Free throws per penalty foul. */
    val bonusFreeThrows: Int = 2,
    /** NCAA men: the first penalty level is a one-and-one. */
    val oneAndOne: Boolean = false,

    // --- Timeouts -------------------------------------------------------------------------
    val timeoutLengthMs: Long = 75 * SEC,
    val timeoutsFirstHalf: Int = 7,
    val timeoutsSecondHalf: Int = 0,
    val timeoutsPerOvertime: Int = 2,
    val timeoutsCarryToSecondHalf: Boolean = true,
    val timeoutsCarryIntoOvertime: Boolean = false,
    /** Caps in the last regulation period (FIBA: max 2 at ≤2:00; NBA: max 4, max 2 at ≤3:00). */
    val timeoutCaps: List<TimeoutCap> = emptyList(),
    /** Warning signal this long before a timeout ends (FIBA: at 50 s of 60 s). 0 = off. */
    val timeoutWarningMs: Long = 0,
    /** Short (30-second) timeouts per game, carried over (NCAA). */
    val shortTimeoutsPerGame: Int = 0,
    val shortTimeoutsPerOvertime: Int = 0,
    val shortTimeoutLengthMs: Long = 30 * SEC,

    // --- Shot clock -----------------------------------------------------------------------
    val shotClockEnabled: Boolean = true,
    val shotClockFullMs: Long = 24 * SEC,
    val shotClockShortMs: Long = 14 * SEC,
    /** The shot clock is switched off (blank, no horn) while the game clock shows less. */
    val shotClockOffWhenLower: Boolean = false,

    // --- Display --------------------------------------------------------------------------
    val lastMinuteWarningMs: Long = 60 * SEC,
    /** Game clock shows tenths of a second below this. */
    val tenthsUnderMs: Long = 60 * SEC,

    // --- Late-game clock stops after a made basket -------------------------------------------
    /** Last regulation period and every overtime: the clock stops on a score at or below this. 0 = never. */
    val scoreStopFinalMs: Long = 2 * MIN,
    /** Any other period. 0 = never. */
    val scoreStopOtherMs: Long = 1 * MIN,
) {
    val regulationPeriods: Int get() = format.regulationPeriods
    val halfPeriod: Int get() = regulationPeriods / 2
    val isThreeByThree: Boolean get() = kind == GameKind.THREE_X_THREE

    /** Point values offered on the score buttons. */
    val scoringPoints: List<Int> get() = when {
        !isThreeByThree -> listOf(1, 2, 3)
        arcScoring -> listOf(2, 3)
        else -> listOf(1, 2)
    }

    /** True when [clockMs] in [period] is inside the official "clock stops after a score" window. */
    fun inLateWindow(period: Int, clockMs: Long): Boolean {
        val w = if (period >= regulationPeriods) scoreStopFinalMs else scoreStopOtherMs
        return w > 0 && clockMs <= w
    }

    /** Plain-language description of the late-game window, for settings screens. */
    fun lateWindowText(): String {
        fun fmt(ms: Long): String {
            val s = ms / 1000
            return if (ms % 1000 != 0L) "${s / 60}:${(s % 60).toString().padStart(2, '0')}.${(ms % 1000) / 100}"
            else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }
        val last = when (format) {
            PeriodFormat.HALVES -> "the 2nd half"
            PeriodFormat.SINGLE -> "regular time"
            PeriodFormat.QUARTERS -> "the 4th quarter"
        }
        val parts = mutableListOf<String>()
        if (scoreStopFinalMs > 0) parts += "last ${fmt(scoreStopFinalMs)} of $last and overtime"
        if (scoreStopOtherMs > 0) parts += "last ${fmt(scoreStopOtherMs)} of other periods"
        return if (parts.isEmpty()) "no late-game window in these rules" else parts.joinToString("; ")
    }

    fun isOvertime(period: Int) = period > regulationPeriods

    /** Overtime without a game clock (3x3: ends when a team scores [overtimeWinPoints]). */
    fun isUntimed(period: Int) = isOvertime(period) && overtimeLengthMs == 0L

    fun lengthOf(period: Int) = when {
        isUntimed(period) -> UNTIMED_MS
        isOvertime(period) -> overtimeLengthMs
        else -> periodLengthMs
    }

    /** Interval after [period] ends. */
    fun intervalAfter(period: Int): Long = when {
        period >= regulationPeriods -> overtimeIntervalMs
        period == halfPeriod -> halftimeMs
        else -> intervalMs
    }

    /** The rule for [type]; types missing from the table count as a personal, 1 team foul. */
    fun foulRule(type: FoulType): FoulRule =
        foulTable?.firstOrNull { it.type == type } ?: legacyFoulTable().firstOrNull { it.type == type }
        ?: FoulRule(type, type.label, countsPersonal = true, teamFouls = 1)

    /** Foul types offered when recording a foul, in table order. */
    val foulTypes: List<FoulType> get() = (foulTable ?: legacyFoulTable()).map { it.type }

    val ejectionRules: List<EjectionRule> get() = ejections ?: legacyEjections()

    fun validated(): Rules {
        val untimedOt = isThreeByThree && overtimeLengthMs == 0L
        val r = copy(
            periodLengthMs = periodLengthMs.coerceIn(1 * MIN, 60 * MIN),
            overtimeLengthMs = if (untimedOt) 0 else overtimeLengthMs.coerceIn(1 * MIN, 20 * MIN),
            playerFoulLimit = playerFoulLimit.coerceIn(0, 20),
            playerFoulWarnAt = playerFoulWarnAt.coerceIn(0, playerFoulLimit.coerceIn(0, 20)),
            bonusAt = bonusAt.coerceIn(1, 30),
            overtimeBonusAt = overtimeBonusAt.coerceIn(1, 30),
            timeoutLengthMs = timeoutLengthMs.coerceIn(10 * SEC, 5 * MIN),
            shotClockFullMs = shotClockFullMs.coerceIn(5 * SEC, 60 * SEC),
            shotClockShortMs = shotClockShortMs.coerceIn(5 * SEC, shotClockFullMs.coerceIn(5 * SEC, 60 * SEC)),
            intervalMs = intervalMs.coerceIn(0, 30 * MIN),
            halftimeMs = halftimeMs.coerceIn(0, 30 * MIN),
            overtimeIntervalMs = overtimeIntervalMs.coerceIn(0, 30 * MIN),
            targetScore = targetScore.coerceIn(0, 200),
            overtimeWinPoints = overtimeWinPoints.coerceIn(0, 20),
            tenthsUnderMs = tenthsUnderMs.coerceIn(0, 5 * MIN),
        )
        // Materialise the foul table for games saved before it existed, so the rest of the
        // engine only ever reads the table.
        return if (r.foulTable == null || r.ejections == null) {
            r.copy(foulTable = r.foulTable ?: r.legacyFoulTable(), ejections = r.ejections ?: r.legacyEjections())
        } else r
    }

    /** Equivalent of the pre-table behaviour, from the legacy flags. */
    private fun legacyFoulTable(): List<FoulRule> = listOf(
        FoulRule(FoulType.PERSONAL, "Personal", countsPersonal = true, teamFouls = 1),
        FoulRule(FoulType.TECHNICAL, "Technical", countsPersonal = technicalCountsAsPersonal,
            teamFouls = if (technicalCountsAsTeamFoul) 1 else 0, benchTeamFouls = 0, freeThrows = 1),
        FoulRule(FoulType.FLAGRANT, "Flagrant", countsPersonal = true, teamFouls = 1, freeThrows = 2, possession = true),
        FoulRule(FoulType.UNSPORTSMANLIKE, "Unsportsmanlike", countsPersonal = true, teamFouls = 1, freeThrows = 2, possession = true),
    )

    private fun legacyEjections(): List<EjectionRule> = buildList {
        if (technicalsForEjection > 0) add(EjectionRule(mapOf(FoulType.TECHNICAL to 1), technicalsForEjection, "$technicalsForEjection technicals"))
        if (flagrantsForEjection > 0) add(EjectionRule(mapOf(FoulType.FLAGRANT to 1), flagrantsForEjection, "$flagrantsForEjection flagrants"))
        if (unsportsmanlikeForEjection > 0) add(EjectionRule(mapOf(FoulType.UNSPORTSMANLIKE to 1), unsportsmanlikeForEjection, "$unsportsmanlikeForEjection unsportsmanlike"))
        if (technicalPlusUnsportsmanlikeEjects) add(EjectionRule(mapOf(FoulType.TECHNICAL to 2, FoulType.UNSPORTSMANLIKE to 3), 5, "T + U"))
    }

    companion object {
        const val SEC = 1_000L
        const val MIN = 60_000L

        /** Countdown base for untimed periods: never reaches zero in a real game. */
        const val UNTIMED_MS = 99 * MIN

        /** Selectable period lengths offered in settings UIs. */
        val PERIOD_LENGTH_CHOICES_MIN = listOf(5, 6, 7, 8, 10, 12, 15, 16, 20)
        val OVERTIME_LENGTH_CHOICES_MIN = listOf(2, 3, 4, 5)

        val selectablePresets: List<RulePreset> get() = RulePreset.entries.filter { it.selectable && it != RulePreset.CUSTOM }

        fun preset(p: RulePreset): Rules = when (p) {
            RulePreset.NBA -> Presets.nba
            RulePreset.FIBA, RulePreset.NFHS -> Presets.fiba.copy(preset = p)
            RulePreset.NCAA -> Presets.ncaaMen
            RulePreset.NCAA_W -> Presets.ncaaWomen
            RulePreset.FIBA_3X3 -> Presets.fiba3x3
            RulePreset.CUSTOM -> Presets.fiba.copy(preset = RulePreset.CUSTOM)
        }.validated()
    }
}
