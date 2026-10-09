package com.watchthetime.domain.state

import com.watchthetime.domain.event.Stamp
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.Rules

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
    val technicals: Int get() = fouls.count { it.type == FoulType.TECHNICAL }
    val flagrants: Int get() = fouls.count { it.type == FoulType.FLAGRANT }
    val unsportsmanlike: Int get() = fouls.count { it.type == FoulType.UNSPORTSMANLIKE }

    fun countedFouls(rules: Rules): Int =
        fouls.count { it.type != FoulType.TECHNICAL || rules.technicalCountsAsPersonal }

    fun disqualified(rules: Rules): Boolean {
        if (countedFouls(rules) >= rules.playerFoulLimit) return true
        if (rules.technicalsForEjection > 0 && technicals >= rules.technicalsForEjection) return true
        if (rules.flagrantsForEjection > 0 && flagrants >= rules.flagrantsForEjection) return true
        if (rules.unsportsmanlikeForEjection > 0 && unsportsmanlike >= rules.unsportsmanlikeForEjection) return true
        if (rules.technicalPlusUnsportsmanlikeEjects && technicals >= 1 && unsportsmanlike >= 1) return true
        return false
    }

    /** True when one more counted foul fouls the player out (and they're not already out). */
    fun inFoulTrouble(rules: Rules): Boolean =
        !disqualified(rules) && rules.playerFoulWarnAt > 0 && countedFouls(rules) >= rules.playerFoulWarnAt

    /** Flags to render as badges, in order: T, F, U with counts. */
    fun flagBadges(): List<Pair<FoulType, Int>> = listOf(
        FoulType.TECHNICAL to technicals,
        FoulType.FLAGRANT to flagrants,
        FoulType.UNSPORTSMANLIKE to unsportsmanlike,
    ).filter { it.second > 0 }
}

data class TeamState(
    val side: TeamSide,
    val info: TeamInfo,
    val score: Int = 0,
    val scoreByPeriod: Map<Int, Int> = emptyMap(),
    val fouls: List<FoulRecord> = emptyList(),
    /** Period of every timeout taken. */
    val timeoutsTaken: List<Int> = emptyList(),
    /** (period, delta) manual corrections. */
    val timeoutAdjustments: List<Pair<Int, Int>> = emptyList(),
)

data class ActiveTimeout(val side: TeamSide, val countdown: Countdown, val eventId: String)

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

    /** Active (non-removed) roster of a side, sorted by jersey number. */
    fun roster(side: TeamSide): List<PlayerState> =
        players.values.filter { it.info.side == side && !it.removed }
            .sortedWith(compareBy({ it.info.number.toIntOrNull() ?: 999 }, { it.info.number.length }, { it.info.number }))

    fun player(id: String?): PlayerState? = id?.let { players[it] }

    fun playerByNumber(side: TeamSide, number: String): PlayerState? =
        players.values.firstOrNull { it.info.side == side && !it.removed && it.info.number == number }

    // ---- Team fouls & bonus ----------------------------------------------------------------

    private fun foulKey(p: Int): Int =
        if (rules.isOvertime(p) && rules.overtimeFoulsCarry) rules.regulationPeriods else p

    private fun countsAsTeamFoul(f: FoulRecord): Boolean =
        f.type != FoulType.TECHNICAL || (rules.technicalCountsAsTeamFoul && f.playerId != null)

    fun teamFouls(side: TeamSide, period: Int = this.period): Int {
        val key = foulKey(period)
        return team(side).fouls.count { foulKey(it.period) == key && countsAsTeamFoul(it) }
    }

    /** Foul number on which free throws start for [period]. */
    fun bonusThreshold(period: Int = this.period): Int =
        if (rules.isOvertime(period) && !rules.overtimeFoulsCarry) rules.overtimeBonusAt else rules.bonusAt

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
                it.period == period && it.gameClockMs <= LAST_TWO_MINUTES_MS && countsAsTeamFoul(it)
            }
            if (lateFouls >= 1) return PenaltyLevel.BONUS
        }
        return PenaltyLevel.NONE
    }

    /** Whether [side] is shooting bonus free throws (its opponent is in the penalty). */
    fun inBonus(side: TeamSide): PenaltyLevel = penalty(side.other)

    // ---- Timeouts -----------------------------------------------------------------------------

    private fun segmentOf(p: Int): Int = when {
        p <= rules.halfPeriod -> 1
        p <= rules.regulationPeriods -> 2
        else -> 2 + (p - rules.regulationPeriods)
    }

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
            val used = t.timeoutsTaken.count { segmentOf(it) == seg }
            remaining = (base + adj - used).coerceAtLeast(0)
            carry = remaining
        }
        return remaining
    }

    // ---- Periods ------------------------------------------------------------------------------

    val isTied: Boolean get() = team(TeamSide.HOME).score == team(TeamSide.AWAY).score

    fun nextPeriodAction(): PeriodAction = when {
        period < rules.regulationPeriods -> PeriodAction.Next(period + 1)
        isTied -> PeriodAction.Overtime(period + 1)
        else -> PeriodAction.Final
    }

    companion object {
        const val LAST_TWO_MINUTES_MS = 120_000L
    }
}
