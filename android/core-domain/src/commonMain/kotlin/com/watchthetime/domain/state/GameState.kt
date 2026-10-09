package com.watchthetime.domain.state

import com.watchthetime.domain.event.Stamp
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.EjectionRule
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.rules.TeamFoulScope

/**
 * A countdown expressed as "[baseMs] remaining at [anchor]". When [anchor] is null the
 * countdown is frozen. Remaining time is always *computed*, never ticked, so it cannot drift.
 */
data class Countdown(val baseMs: Long, val anchor: Stamp? = null) {
    val running: Boolean get() = anchor != null

    fun at(now: Stamp): Long = if (anchor == null) baseMs else (baseMs - now.elapsedSince(anchor)).coerceAtLeast(0)

    fun freeze(now: Stamp) = Countdown(at(now), null)

    fun start(now: Stamp) = if (running) this else Countdown(baseMs, now)

    /** Re-anchors at [now] with [delta] applied, preserving running/stopped state. */
    fun adjust(deltaMs: Long, now: Stamp, maxMs: Long) =
        Countdown((at(now) + deltaMs).coerceIn(0, maxMs), anchor?.let { now })

    fun set(valueMs: Long, now: Stamp) = Countdown(valueMs.coerceAtLeast(0), anchor?.let { now })

    /** The stamp at which this countdown reaches zero, or null when frozen. */
    fun deadline(): Stamp? = anchor?.plus(baseMs)
}

data class FoulRecord(
    val eventId: String,
    val type: FoulType,
    val period: Int,
    val gameClockMs: Long,
    val playerId: String?,
)

data class PlayerState(
    val info: PlayerInfo,
    val removed: Boolean = false,
    val made1: Int = 0,
    val made2: Int = 0,
    val made3: Int = 0,
    /** Free throws (from +1 FT scores and free-throw sets). */
    val ftMade: Int = 0,
    val ftAttempted: Int = 0,
    val fouls: List<FoulRecord> = emptyList(),
) {
    val id: String get() = info.id
    val points: Int get() = made1 + 2 * made2 + 3 * made3
    val technicals: Int get() = fouls.count { it.type == FoulType.TECHNICAL || it.type == FoulType.TECHNICAL_2 }
    val flagrants: Int get() = fouls.count { it.type == FoulType.FLAGRANT || it.type == FoulType.FLAGRANT_2 }
    val unsportsmanlike: Int get() = fouls.count { it.type == FoulType.UNSPORTSMANLIKE || it.type == FoulType.DISRUPTIVE }

    fun count(type: FoulType): Int = fouls.count { it.type == type }

    fun countedFouls(rules: Rules): Int = fouls.count { rules.foulRule(it.type).countsPersonal }

    /** The ejection rule this player has met, or null. */
    fun ejectedBy(rules: Rules): EjectionRule? = rules.ejectionRules.firstOrNull { r ->
        r.weights.entries.sumOf { (type, w) -> count(type) * w } >= r.threshold
    }

    fun fouledOut(rules: Rules): Boolean = rules.playerFoulLimit > 0 && countedFouls(rules) >= rules.playerFoulLimit

    fun disqualified(rules: Rules): Boolean = fouledOut(rules) || ejectedBy(rules) != null

    /** True when one more counted foul fouls the player out (and they're not already out). */
    fun inFoulTrouble(rules: Rules): Boolean =
        !disqualified(rules) && rules.playerFoulLimit > 0 && rules.playerFoulWarnAt > 0 && countedFouls(rules) >= rules.playerFoulWarnAt

    /** Flags to render as badges (every non-personal, non-offensive type) with counts. */
    fun flagBadges(): List<Pair<FoulType, Int>> =
        FoulType.entries.filter { it.isFlag }.map { it to count(it) }.filter { it.second > 0 }
}


/** One timeout taken: when, and whether it was a short (30-second) one. */
data class TimeoutRecord(val period: Int, val gameClockMs: Long, val short: Boolean = false)

data class TeamState(
    val side: TeamSide,
    val info: TeamInfo,
    val score: Int = 0,
    val scoreByPeriod: Map<Int, Int> = emptyMap(),
    val fouls: List<FoulRecord> = emptyList(),
    val timeoutsTaken: List<TimeoutRecord> = emptyList(),
    /** (period, delta) manual corrections of full timeouts. */
    val timeoutAdjustments: List<Pair<Int, Int>> = emptyList(),
)

data class ActiveTimeout(val side: TeamSide, val countdown: Countdown, val eventId: String, val short: Boolean = false)

enum class GameStatus { NOT_CREATED, PREGAME, LIVE, PERIOD_BREAK, FINAL }

/** Penalty state of the *fouling* team (i.e. its opponent shoots free throws). */
enum class PenaltyLevel { NONE, BONUS, DOUBLE_BONUS }

/** What the "next period" control will do from the current state. */
sealed interface PeriodAction {
    data class Next(val period: Int) : PeriodAction
    data class Overtime(val period: Int) : PeriodAction
    data object Final : PeriodAction
}

data class GameState(
    val gameId: String,
    val created: Boolean = false,
    val title: String = "",
    val rules: Rules = Rules(),
    val clockPolicy: ClockPolicy = ClockPolicy(),
    val teams: Map<TeamSide, TeamState> = TeamSide.entries.associateWith { TeamState(it, TeamInfo.default(it)) },
    val players: Map<String, PlayerState> = emptyMap(),
    val period: Int = 1,
    val clock: Countdown = Countdown(rules.periodLengthMs),
    val shot: Countdown = Countdown(rules.shotClockFullMs),
    val shotHeld: Boolean = false,
    val timeout: ActiveTimeout? = null,
    val everStarted: Boolean = false,
    val finalized: Boolean = false,
    /** When the current period ended (interval timer start); null while a period is in play. */
    val breakSince: Stamp? = null,
    val appliedEvents: Int = 0,
) {
    fun team(side: TeamSide): TeamState = teams.getValue(side)

    val status: GameStatus
        get() = when {
            !created -> GameStatus.NOT_CREATED
            finalized -> GameStatus.FINAL
            !everStarted && period == 1 -> GameStatus.PREGAME
            !clock.running && clock.baseMs == 0L -> GameStatus.PERIOD_BREAK
            else -> GameStatus.LIVE
        }

    val shotClockActive: Boolean get() = rules.shotClockEnabled

    /** Shot clock switched off because the game clock shows less time (rules permitting). */
    fun shotClockOff(now: Stamp): Boolean =
        !rules.shotClockEnabled || (rules.shotClockOffWhenLower && clock.at(now) < shot.at(now))

    /** Untimed period (3x3 overtime): the game clock only drives the shot clock. */
    val untimed: Boolean get() = rules.isUntimed(period)

    /** Remaining interval time after a period ended, or null when not in an interval. */
    fun intervalRemaining(now: Stamp): Long? {
        val since = breakSince ?: return null
        if (finalized || status != GameStatus.PERIOD_BREAK) return null
        val len = rules.intervalAfter(period)
        if (len <= 0) return null
        return (len - now.elapsedSince(since)).coerceAtLeast(0)
    }

    /** Active (non-removed) roster of a side, sorted by jersey number. */
    fun roster(side: TeamSide): List<PlayerState> =
        players.values.filter { it.info.side == side && !it.removed }
            .sortedWith(compareBy({ it.info.number.toIntOrNull() ?: 999 }, { it.info.number.length }, { it.info.number }))

    fun player(id: String?): PlayerState? = id?.let { players[it] }

    fun playerByNumber(side: TeamSide, number: String): PlayerState? =
        players.values.firstOrNull { it.info.side == side && !it.removed && it.info.number == number }

    // ---- Team fouls & bonus ----------------------------------------------------------------

    private fun foulKey(p: Int): Int = when {
        rules.teamFoulScope == TeamFoulScope.GAME -> 0
        rules.isOvertime(p) && rules.overtimeFoulsCarry -> rules.regulationPeriods
        else -> p
    }

    /** Team-foul weight of one foul (0 = not a team foul; 3x3 unsportsmanlike = 2). */
    fun teamFoulWeight(f: FoulRecord): Int {
        val r = rules.foulRule(f.type)
        return if (f.playerId != null) r.teamFouls else r.benchTeamFouls
    }

    fun teamFouls(side: TeamSide, period: Int = this.period): Int {
        val key = foulKey(period)
        return team(side).fouls.filter { foulKey(it.period) == key }.sumOf { teamFoulWeight(it) }
    }

    /** Foul number on which free throws start for [period]. */
    fun bonusThreshold(period: Int = this.period): Int =
        if (rules.isOvertime(period) && !rules.overtimeFoulsCarry && rules.teamFoulScope == TeamFoulScope.PERIOD) rules.overtimeBonusAt
        else rules.bonusAt

    /**
     * Penalty state of the fouling [side]: BONUS means the *next* common foul by [side]
     * awards free throws to the opponent.
     */
    fun penalty(side: TeamSide, period: Int = this.period): PenaltyLevel {
        val n = teamFouls(side, period)
        if (rules.doubleBonusAt > 0 && n >= rules.doubleBonusAt - 1) return PenaltyLevel.DOUBLE_BONUS
        if (n >= bonusThreshold(period) - 1) return PenaltyLevel.BONUS
        if (rules.lastTwoMinutesRule) {
            val lateFouls = team(side).fouls.count {
                it.period == period && it.gameClockMs <= LAST_TWO_MINUTES_MS && teamFoulWeight(it) > 0
            }
            if (lateFouls >= 1) return PenaltyLevel.BONUS
        }
        return PenaltyLevel.NONE
    }

    /** Whether [side] is shooting bonus free throws (its opponent is in the penalty). */
    fun inBonus(side: TeamSide): PenaltyLevel = penalty(side.other)

    /** Short text for what the next penalty foul by the opponent of [side] gives [side]. */
    fun bonusText(side: TeamSide): String? = when (inBonus(side)) {
        PenaltyLevel.NONE -> null
        PenaltyLevel.BONUS -> if (rules.oneAndOne) "1 + 1" else "Bonus"
        PenaltyLevel.DOUBLE_BONUS -> if (rules.isThreeByThree) "2 FT + ball" else "2× Bonus"
    }

    // ---- Timeouts -----------------------------------------------------------------------------

    private fun segmentOf(p: Int): Int = when {
        p <= rules.halfPeriod -> 1
        p <= rules.regulationPeriods -> 2
        else -> 2 + (p - rules.regulationPeriods)
    }

    /** Full timeouts left by allocation (before late-game caps). */
    fun timeoutsRemaining(side: TeamSide, period: Int = this.period): Int {
        val t = team(side)
        val target = segmentOf(period)
        var carry = 0
        var remaining = 0
        for (seg in 1..target) {
            val base = when (seg) {
                1 -> rules.timeoutsFirstHalf
                2 -> rules.timeoutsSecondHalf + if (rules.timeoutsCarryToSecondHalf) carry else 0
                else -> rules.timeoutsPerOvertime + if (rules.timeoutsCarryIntoOvertime) carry else 0
            }
            val adj = t.timeoutAdjustments.filter { segmentOf(it.first) == seg }.sumOf { it.second }
            val used = t.timeoutsTaken.count { !it.short && segmentOf(it.period) == seg }
            remaining = (base + adj - used).coerceAtLeast(0)
            carry = remaining
        }
        return remaining
    }

    /** Short (30 s) timeouts left: per game plus per overtime reached, all carried over. */
    fun shortTimeoutsRemaining(side: TeamSide, period: Int = this.period): Int {
        if (rules.shortTimeoutsPerGame == 0 && rules.shortTimeoutsPerOvertime == 0) return 0
        val ots = (period - rules.regulationPeriods).coerceAtLeast(0)
        val used = team(side).timeoutsTaken.count { it.short && it.period <= period }
        return (rules.shortTimeoutsPerGame + ots * rules.shortTimeoutsPerOvertime - used).coerceAtLeast(0)
    }

    /**
     * Timeouts [side] may call right now with [clockMs] on the game clock: the allocation,
     * limited by the late-game caps of the last regulation period (FIBA / NBA).
     */
    fun timeoutsAvailable(side: TeamSide, clockMs: Long, short: Boolean = false): Int {
        var left = if (short) shortTimeoutsRemaining(side) else timeoutsRemaining(side)
        if (period == rules.regulationPeriods && rules.regulationPeriods > 1) {
            for (cap in rules.timeoutCaps) {
                if (clockMs > cap.underMs) continue
                val taken = team(side).timeoutsTaken.count { it.period == period && it.gameClockMs <= cap.underMs }
                left = minOf(left, (cap.max - taken).coerceAtLeast(0))
            }
        }
        return left
    }

    // ---- Periods ------------------------------------------------------------------------------

    val isTied: Boolean get() = team(TeamSide.HOME).score == team(TeamSide.AWAY).score

    fun pointsIn(side: TeamSide, period: Int): Int = team(side).scoreByPeriod[period] ?: 0

    /** 3x3-style immediate win: target score in regulation, or N points scored in overtime. */
    fun winner(): TeamSide? {
        if (rules.targetScore > 0 && !rules.isOvertime(period)) {
            TeamSide.entries.firstOrNull { team(it).score >= rules.targetScore }?.let { return it }
        }
        if (rules.overtimeWinPoints > 0 && rules.isOvertime(period)) {
            TeamSide.entries.firstOrNull { pointsIn(it, period) >= rules.overtimeWinPoints }?.let { return it }
        }
        return null
    }

    fun nextPeriodAction(): PeriodAction = when {
        period < rules.regulationPeriods -> PeriodAction.Next(period + 1)
        isTied -> PeriodAction.Overtime(period + 1)
        else -> PeriodAction.Final
    }

    companion object {
        const val LAST_TWO_MINUTES_MS = 120_000L
    }
}
