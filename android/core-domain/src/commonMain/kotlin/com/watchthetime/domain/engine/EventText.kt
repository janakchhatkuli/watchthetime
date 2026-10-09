package com.watchthetime.domain.engine

import com.watchthetime.domain.event.*
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState

/** Human-readable one-liners for the event log, CSV and accessibility labels. */
object EventText {

    fun describe(e: GameEvent, s: GameState): String {
        fun team(side: TeamSide) = s.team(side).info.shortName
        fun who(id: String?) = s.player(id)?.let { "#${it.info.number}${if (it.info.name.isNotBlank()) " " + it.info.name else ""}" }
        return when (val p = e.payload) {
            is GameCreated -> "Game created · ${p.home.shortName} vs ${p.away.shortName}"
            is RulesUpdated -> "Rules changed (${p.rules.preset.label})"
            is ClockPolicyChanged -> "Clock mode → ${p.policy.summary}"
            is TeamUpdated -> "Team ${p.side.tag}: ${p.info.name}"
            is PlayerUpserted -> "Roster ${team(p.player.side)}: ${p.player.label}"
            is PlayerRemoved -> "Roster: removed ${who(p.playerId) ?: "player"}"
            ClockStarted -> "Clock start"
            is ClockStopped -> when {
                p.expired -> "Buzzer: period expired"
                p.auto -> "Clock stop (auto)"
                else -> "Clock stop"
            }
            is ClockAdjusted -> "Clock ${if (p.deltaMs >= 0) "+" else "−"}${kotlin.math.abs(p.deltaMs) / 1000.0}s"
            is ClockSet -> "Clock set ${ClockFormat.plain(p.remainingMs, true)}"
            is PeriodSet -> "Start ${ClockFormat.periodLong(p.period, s.rules).lowercase()}"
            PeriodEnded -> "Period ended"
            GameFinalized -> "Final"
            GameReopened -> "Game reopened"
            is ShotClockReset -> "Shot clock ${p.valueMs / 1000}"
            is ShotClockHold -> if (p.held) "Shot clock hold" else "Shot clock run"
            is ShotClockAdjusted -> "Shot clock ${if (p.deltaMs >= 0) "+" else "−"}${kotlin.math.abs(p.deltaMs) / 1000}s"
            is Score -> "+${p.points}${shotText(p)} ${team(p.side)}${who(p.playerId)?.let { " $it" } ?: ""}"
            is FreeThrows -> "Free throws ${p.made}/${p.attempts} ${team(p.side)}${who(p.playerId)?.let { " $it" } ?: ""}"
            is Substitution -> "Substitution ${team(p.side)}" +
                (who(p.playerIn)?.let { " in $it" } ?: "") + (who(p.playerOut)?.let { " out $it" } ?: "")
            is Foul -> "${p.type.label} foul ${team(p.side)} ${who(p.playerId) ?: "bench/team"}"
            is TimeoutStarted -> "Timeout ${team(p.side)}"
            is TimeoutEnded -> "Timeout over ${team(p.side)}"
            is TimeoutsAdjusted -> "Timeouts ${team(p.side)} ${if (p.delta >= 0) "+" else ""}${p.delta}"
        }
    }

    /** " FT", " 3PT", " 2PT" (3x3 arc) or "" for an ordinary field goal. */
    fun shotText(p: Score): String = when (p.shotOf()) {
        ShotType.FREE_THROW -> " FT"
        ShotType.ARC -> " ${p.points}PT"
        ShotType.FIELD_GOAL -> ""
    }

    /** "Q2 4:31" prefix. */
    fun whenText(e: GameEvent, s: GameState) =
        "${ClockFormat.periodShort(e.period, s.rules)} ${ClockFormat.plain(e.gameClockMs, true)}"

    /** Display order for logs: newest first (by seq). */
    fun logOrder(events: Collection<GameEvent>): List<GameEvent> = events.sortedWith(Reducer.ORDER).asReversed()
}
