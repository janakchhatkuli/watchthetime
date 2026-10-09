package com.watchthetime.domain.engine

import com.watchthetime.domain.event.*
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.*

/**
 * Pure left-fold of events into [GameState]. Deterministic: the same event list always yields
 * the same state, on any device. Deleted events are skipped; order is (seq, id).
 */
object Reducer {

    val ORDER: Comparator<GameEvent> = compareBy<GameEvent>({ it.seq }, { it.id })

    fun replay(gameId: String, events: Collection<GameEvent>): GameState =
        events.asSequence()
            .filter { !it.deleted && it.gameId == gameId }
            .sortedWith(ORDER)
            .fold(GameState(gameId)) { s, e -> reduce(s, e) }

    fun reduce(s: GameState, e: GameEvent): GameState {
        val next = when (val p = e.payload) {
            is GameCreated -> {
                val rules = p.rules.validated()
                s.copy(
                    created = true,
                    title = p.title,
                    rules = rules,
                    clockPolicy = p.clockPolicy,
                    teams = mapOf(
                        TeamSide.HOME to s.team(TeamSide.HOME).copy(info = p.home),
                        TeamSide.AWAY to s.team(TeamSide.AWAY).copy(info = p.away),
                    ),
                    players = s.players + p.roster.associate { it.id to (s.players[it.id]?.copy(info = it) ?: PlayerState(it)) },
                    clock = if (s.everStarted) s.clock else Countdown(rules.lengthOf(s.period)),
                    shot = if (s.everStarted) s.shot else Countdown(rules.shotClockFullMs),
                )
            }

            is RulesUpdated -> {
                val rules = p.rules.validated()
                if (!s.everStarted && s.period == 1) {
                    s.copy(rules = rules, clock = Countdown(rules.lengthOf(1)), shot = Countdown(rules.shotClockFullMs))
                } else s.copy(rules = rules)
            }

            is TeamUpdated -> s.copy(teams = s.teams + (p.side to s.team(p.side).copy(info = p.info)))

            is ClockPolicyChanged -> s.copy(clockPolicy = p.policy)

            is PlayerUpserted -> {
                val existing = s.players[p.player.id]
                s.copy(players = s.players + (p.player.id to (existing?.copy(info = p.player, removed = false) ?: PlayerState(p.player))))
            }

            is PlayerRemoved -> s.players[p.playerId]?.let {
                s.copy(players = s.players + (p.playerId to it.copy(removed = true)))
            } ?: s

            ClockStarted -> {
                if (s.finalized || s.clock.running || s.clock.baseMs <= 0) s
                else {
                    val shotRuns = s.rules.shotClockEnabled && !s.shotHeld
                    s.copy(
                        clock = s.clock.start(e.at),
                        shot = if (shotRuns) s.shot.start(e.at) else s.shot,
                        everStarted = true,
                    )
                }
            }

            is ClockStopped -> s.copy(
                clock = if (p.expired) Countdown(0) else s.clock.freeze(e.at),
                shot = s.shot.freeze(e.at),
            )

            is ClockAdjusted -> s.copy(clock = s.clock.adjust(p.deltaMs, e.at, s.rules.lengthOf(s.period)))

            is ClockSet -> s.copy(clock = s.clock.set(p.remainingMs.coerceAtMost(s.rules.lengthOf(s.period)), e.at))

            is PeriodSet -> {
                val period = p.period.coerceAtLeast(1)
                s.copy(
                    period = period,
                    clock = Countdown(s.rules.lengthOf(period)),
                    shot = Countdown(s.rules.shotClockFullMs),
                    shotHeld = false,
                    timeout = null,
                    everStarted = s.everStarted || period > 1,
                )
            }

            PeriodEnded -> s.copy(clock = Countdown(0), shot = s.shot.freeze(e.at))

            GameFinalized -> s.copy(finalized = true, clock = s.clock.freeze(e.at), shot = s.shot.freeze(e.at), timeout = null)

            GameReopened -> s.copy(finalized = false)

            is ShotClockReset -> s.copy(
                shot = Countdown(p.valueMs.coerceAtLeast(0), if (s.clock.running && s.rules.shotClockEnabled) e.at else null),
                shotHeld = false,
            )

            is ShotClockHold -> s.copy(
                shotHeld = p.held,
                shot = if (p.held) s.shot.freeze(e.at) else if (s.clock.running) s.shot.start(e.at) else s.shot,
            )

            is ShotClockAdjusted -> s.copy(shot = s.shot.adjust(p.deltaMs, e.at, s.rules.shotClockFullMs))

            is Score -> {
                val pts = p.points.coerceIn(1, 3)
                val ft = p.shotOf() == ShotType.FREE_THROW
                val s1 = addPoints(s, p.side, e.period, pts)
                val players = s1.player(p.playerId)?.takeIf { it.info.side == p.side }?.let { pl ->
                    val base = if (ft) pl.copy(ftMade = pl.ftMade + 1, ftAttempted = pl.ftAttempted + 1) else pl
                    val updated = when (pts) {
                        1 -> base.copy(made1 = base.made1 + 1)
                        2 -> base.copy(made2 = base.made2 + 1)
                        else -> base.copy(made3 = base.made3 + 1)
                    }
                    s1.players + (pl.id to updated)
                } ?: s1.players
                s1.copy(players = players)
            }

            is FreeThrows -> {
                val made = p.made.coerceIn(0, p.attempts.coerceAtLeast(0))
                val s1 = addPoints(s, p.side, e.period, made)
                val players = s1.player(p.playerId)?.takeIf { it.info.side == p.side }?.let { pl ->
                    s1.players + (pl.id to pl.copy(
                        made1 = pl.made1 + made, ftMade = pl.ftMade + made, ftAttempted = pl.ftAttempted + p.attempts.coerceAtLeast(0),
                    ))
                } ?: s1.players
                s1.copy(players = players)
            }

            is Substitution -> s

            is Foul -> {
                val validPlayer = s.player(p.playerId)?.takeIf { it.info.side == p.side }
                val rec = FoulRecord(e.id, p.type, e.period, e.gameClockMs, validPlayer?.id)
                val t = s.team(p.side)
                s.copy(
                    teams = s.teams + (p.side to t.copy(fouls = t.fouls + rec)),
                    players = validPlayer?.let { s.players + (it.id to it.copy(fouls = it.fouls + rec)) } ?: s.players,
                )
            }

            is TimeoutStarted -> {
                val t = s.team(p.side)
                s.copy(
                    teams = s.teams + (p.side to t.copy(timeoutsTaken = t.timeoutsTaken + e.period)),
                    timeout = ActiveTimeout(p.side, Countdown(p.lengthMs, e.at), e.id),
                )
            }

            is TimeoutEnded -> s.copy(timeout = null)

            is TimeoutsAdjusted -> {
                val t = s.team(p.side)
                s.copy(teams = s.teams + (p.side to t.copy(timeoutAdjustments = t.timeoutAdjustments + (e.period to p.delta))))
            }
        }
        return next.copy(appliedEvents = s.appliedEvents + 1)
    }

    private fun addPoints(s: GameState, side: TeamSide, period: Int, pts: Int): GameState {
        if (pts == 0) return s
        val t = s.team(side)
        return s.copy(
            teams = s.teams + (side to t.copy(
                score = t.score + pts,
                scoreByPeriod = t.scoreByPeriod + (period to (t.scoreByPeriod[period] ?: 0) + pts),
            )),
        )
    }
}
