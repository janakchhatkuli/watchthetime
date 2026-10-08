package com.watchthetime.domain.engine

import com.watchthetime.domain.event.*
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.state.Countdown
import com.watchthetime.domain.state.GameState
import com.watchthetime.domain.state.GameStatus
import com.watchthetime.domain.state.PeriodAction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun interface TimeSource {
    fun now(): Stamp
}

data class Outcome(
    /** New event versions to persist and sync, in order. */
    val changed: List<GameEvent> = emptyList(),
    val cues: List<Cue> = emptyList(),
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

@OptIn(ExperimentalUuidApi::class)
fun randomId(): String = Uuid.random().toString()

/**
 * The single writer for one game. Converts [Command]s into events, keeps the derived
 * [GameState], drives time-based transitions in [tick] and owns undo/redo.
 *
 * Not thread-safe by design: callers serialise access (the Android layer confines it to one
 * coroutine dispatcher).
 */
class GameSession(
    val gameId: String,
    initialEvents: Collection<GameEvent>,
    private val time: TimeSource,
    private val origin: String,
    private val newId: () -> String = ::randomId,
    var shotExpiryStopsGame: Boolean = true,
) {
    private val events: MutableMap<String, GameEvent> =
        initialEvents.filter { it.gameId == gameId }.associateBy { it.id }.toMutableMap()

    var state: GameState = Reducer.replay(gameId, events.values)
        private set

    private var nextSeq: Long = (events.values.maxOfOrNull { it.seq } ?: 0L) + 1
    private val history = UndoStack()

    private var lastMinuteArmed: Boolean = state.clock.at(time.now()) > state.rules.lastMinuteWarningMs
    private var shotBuzzedFor: Countdown? = null

    val allEvents: List<GameEvent> get() = events.values.sortedWith(Reducer.ORDER)
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
    val undoLabel: String? get() = history.undoLabel
    val redoLabel: String? get() = history.redoLabel

    fun event(id: String): GameEvent? = events[id]

    // ---------------------------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------------------------

    fun create(rules: Rules, home: TeamInfo, away: TeamInfo, roster: List<PlayerInfo>, title: String = ""): Outcome {
        if (state.created) return Outcome(error = "Game already exists")
        val tx = Tx("Create game")
        tx.append(GameCreated(rules, home, away, roster, title), period = 1, clockMs = rules.lengthOf(1))
        lastMinuteArmed = true
        return Outcome(tx.changes.map { it.after })
    }

    // ---------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------

    fun dispatch(cmd: Command): Outcome {
        if (!state.created) return fail("No game loaded")
        return when (cmd) {
            Command.Undo -> undo()
            Command.Redo -> redo()
            else -> {
                val before = state
                val tx = Tx(labelFor(cmd))
                val cues = mutableListOf<Cue>()
                val err = apply(cmd, tx, cues)
                if (err != null) {
                    // Roll back anything partially written.
                    if (tx.changes.isNotEmpty()) rollback(tx)
                    return fail(err)
                }
                history.push(tx.toTransaction())
                cues += CueDiff.between(before, state)
                rearm()
                Outcome(tx.changes.map { it.after }, cues.sortedByDescending { it.type.priority })
            }
        }
    }

    private fun apply(cmd: Command, tx: Tx, cues: MutableList<Cue>): String? {
        val s = state
        val now = tx.now
        when (cmd) {
            Command.ToggleClock -> return apply(if (s.clock.running) Command.StopClock else Command.StartClock, tx, cues)

            Command.StartClock -> {
                if (s.finalized) return "Game is final"
                if (s.clock.running) return "Clock already running"
                if (s.clock.baseMs <= 0) return "Period over: start next period"
                s.timeout?.let {
                    tx.append(TimeoutEnded(it.side))
                    cues += Cue(CueType.TIMEOUT_END, it.side, text = "TIMEOUT OVER")
                }
                tx.append(ClockStarted)
                cues += Cue(CueType.CLOCK_START, text = "START ${ClockFormat.plain(s.clock.at(now))}")
            }

            Command.StopClock -> {
                if (!s.clock.running) return "Clock already stopped"
                tx.append(ClockStopped())
                cues += Cue(CueType.CLOCK_STOP, text = "STOP ${ClockFormat.plain(state.clock.baseMs, tenths = true)}")
            }

            is Command.AdjustClock -> {
                if (s.finalized) return "Game is final"
                tx.append(ClockAdjusted(cmd.deltaMs))
                val sign = if (cmd.deltaMs >= 0) "+" else "-"
                cues += Cue(CueType.CLOCK_ADJUST, text = "$sign${kotlin.math.abs(cmd.deltaMs) / 1000}s ${ClockFormat.plain(state.clock.at(now), true)}")
            }

            is Command.SetClock -> {
                tx.append(ClockSet(cmd.remainingMs))
                cues += Cue(CueType.CLOCK_ADJUST, text = "CLOCK ${ClockFormat.plain(state.clock.at(now), true)}")
            }

            is Command.ResetShotClock -> {
                if (!s.rules.shotClockEnabled) return "Shot clock is off"
                val v = if (cmd.short) s.rules.shotClockShortMs else s.rules.shotClockFullMs
                tx.append(ShotClockReset(v))
                cues += Cue(CueType.SHOT_CLOCK_RESET, text = "SHOT ${v / 1000}")
            }

            Command.ToggleShotHold -> {
                if (!s.rules.shotClockEnabled) return "Shot clock is off"
                tx.append(ShotClockHold(!s.shotHeld))
                cues += Cue(CueType.SHOT_CLOCK_RESET, text = if (!s.shotHeld) "SHOT HOLD" else "SHOT RUN")
            }

            is Command.AdjustShotClock -> {
                if (!s.rules.shotClockEnabled) return "Shot clock is off"
                tx.append(ShotClockAdjusted(cmd.deltaMs))
                cues += Cue(CueType.CLOCK_ADJUST, text = "SHOT ${ClockFormat.spoken(ClockFormat.shot(state.shot.at(now)))}")
            }

            Command.NextPeriod -> {
                if (s.finalized) return "Game is final"
                val action = s.nextPeriodAction()
                if (s.clock.running) tx.append(ClockStopped())
                if (state.clock.baseMs > 0) tx.append(PeriodEnded)
                s.timeout?.let { tx.append(TimeoutEnded(it.side)) }
                when (action) {
                    is PeriodAction.Next -> {
                        tx.append(PeriodSet(action.period), period = action.period, clockMs = s.rules.lengthOf(action.period))
                        cues += Cue(CueType.PERIOD_ADVANCE, text = ClockFormat.periodLong(action.period, s.rules))
                    }
                    is PeriodAction.Overtime -> {
                        tx.append(PeriodSet(action.period), period = action.period, clockMs = s.rules.lengthOf(action.period))
                        cues += Cue(CueType.PERIOD_ADVANCE, text = ClockFormat.periodLong(action.period, s.rules))
                    }
                    PeriodAction.Final -> {
                        tx.append(GameFinalized)
                        cues += Cue(CueType.GAME_FINAL, text = "FINAL")
                    }
                }
            }

            Command.PreviousPeriod -> {
                if (s.finalized) {
                    tx.append(GameReopened)
                } else {
                    if (s.period <= 1) return "Already in first period"
                    if (s.clock.running) tx.append(ClockStopped())
                    tx.append(PeriodSet(s.period - 1), period = s.period - 1, clockMs = s.rules.lengthOf(s.period - 1))
                }
                cues += Cue(CueType.PERIOD_ADVANCE, text = ClockFormat.periodLong(state.period, s.rules))
            }

            Command.EndPeriod -> {
                if (s.finalized) return "Game is final"
                if (!s.clock.running && s.clock.baseMs == 0L) return "Period already over"
                if (s.clock.running) tx.append(ClockStopped())
                tx.append(PeriodEnded)
                cues += Cue(CueType.PERIOD_END, text = "END ${ClockFormat.periodShort(s.period, s.rules)}")
            }

            Command.FinalizeGame -> {
                if (s.finalized) return "Game is already final"
                if (s.clock.running) tx.append(ClockStopped())
                tx.append(GameFinalized)
                cues += Cue(CueType.GAME_FINAL, text = "FINAL")
            }

            Command.ReopenGame -> {
                if (!s.finalized) return "Game is not final"
                tx.append(GameReopened)
                cues += Cue(CueType.EDIT_SAVED, text = "REOPENED")
            }

            is Command.AddScore -> {
                if (s.finalized) return "Game is final"
                if (cmd.points !in 1..3) return "Invalid points"
                tx.append(Score(cmd.side, cmd.points, cmd.playerId))
                val who = s.player(cmd.playerId)?.let { " #${it.info.number}" } ?: ""
                cues += CueDiff.forScore(cmd.points, cmd.side, "+${cmd.points} ${s.team(cmd.side).info.shortName}$who")
            }

            is Command.AddFoul -> {
                if (s.finalized) return "Game is final"
                tx.append(Foul(cmd.side, cmd.playerId, cmd.type))
                val p = state.player(cmd.playerId)
                val who = p?.let { "#${it.info.number}" } ?: "${s.team(cmd.side).info.shortName} BENCH"
                val count = p?.let { " · ${it.countedFouls(state.rules)} PF" } ?: ""
                cues += CueDiff.forFoul(cmd.type, cmd.side, "$who ${cmd.type.code}$count")
            }

            is Command.StartTimeout -> {
                if (s.finalized) return "Game is final"
                if (s.timeout != null) return "Timeout already running"
                if (s.timeoutsRemaining(cmd.side) <= 0) return "${s.team(cmd.side).info.shortName}: no timeouts left"
                if (s.clock.running) {
                    tx.append(ClockStopped())
                    cues += Cue(CueType.CLOCK_STOP, text = "STOP")
                }
                tx.append(TimeoutStarted(cmd.side, s.rules.timeoutLengthMs))
                cues += Cue(CueType.TIMEOUT_START, cmd.side, text = "TIMEOUT ${s.team(cmd.side).info.shortName} · ${state.timeoutsRemaining(cmd.side)} LEFT")
            }

            Command.EndTimeout -> {
                val t = s.timeout ?: return "No timeout running"
                tx.append(TimeoutEnded(t.side))
                cues += Cue(CueType.TIMEOUT_END, t.side, text = "TIMEOUT OVER")
            }

            is Command.AdjustTimeouts -> {
                tx.append(TimeoutsAdjusted(cmd.side, cmd.delta))
                cues += Cue(CueType.EDIT_SAVED, cmd.side, text = "${s.team(cmd.side).info.shortName} T/O ${state.timeoutsRemaining(cmd.side)}")
            }

            is Command.UpdateTeam -> {
                tx.append(TeamUpdated(cmd.side, cmd.info.copy(shortName = cmd.info.shortName.take(5).uppercase())))
                cues += Cue(CueType.EDIT_SAVED, cmd.side, text = "TEAM SAVED")
            }

            is Command.UpsertPlayer -> {
                val number = cmd.player.number.trim()
                if (number.isEmpty() || number.length > 2 || !number.all { it.isDigit() }) return "Jersey must be 0–99 or 00"
                val clash = s.playerByNumber(cmd.player.side, number)
                if (clash != null && clash.id != cmd.player.id) return "#$number already on roster"
                tx.append(PlayerUpserted(cmd.player.copy(number = number, name = cmd.player.name.trim())))
                cues += Cue(CueType.EDIT_SAVED, cmd.player.side, cmd.player.id, "#$number SAVED")
            }

            is Command.RemovePlayer -> {
                val p = s.player(cmd.playerId) ?: return "Unknown player"
                tx.append(PlayerRemoved(cmd.playerId))
                cues += Cue(CueType.EDIT_SAVED, p.info.side, p.id, "#${p.info.number} REMOVED")
            }

            is Command.UpdateRules -> {
                tx.append(RulesUpdated(cmd.rules))
                cues += Cue(CueType.EDIT_SAVED, text = "RULES SAVED")
            }

            is Command.EditEvent -> {
                val cur = events[cmd.eventId] ?: return "Unknown event"
                if (cur.payload is GameCreated && cmd.payload !is GameCreated) return "Cannot change game setup type"
                tx.replace(
                    cur, cur.copy(
                        payload = cmd.payload,
                        period = cmd.period ?: cur.period,
                        gameClockMs = cmd.gameClockMs ?: cur.gameClockMs,
                    )
                )
                cues += Cue(CueType.EDIT_SAVED, text = "EDIT SAVED")
            }

            is Command.AssignPlayer -> {
                val cur = events[cmd.eventId] ?: return "Unknown event"
                val payload = when (val p = cur.payload) {
                    is Score -> p.copy(playerId = cmd.playerId)
                    is Foul -> p.copy(playerId = cmd.playerId)
                    else -> return "Only scores and fouls have players"
                }
                tx.replace(cur, cur.copy(payload = payload))
                val who = s.player(cmd.playerId)?.let { "#${it.info.number}" } ?: "TEAM"
                cues += Cue(CueType.EDIT_SAVED, text = "→ $who")
            }

            is Command.DeleteEvent -> {
                val cur = events[cmd.eventId] ?: return "Unknown event"
                if (cur.payload is GameCreated) return "Game setup can't be deleted"
                if (cur.deleted) return "Already deleted"
                tx.replace(cur, cur.copy(deleted = true))
                cues += Cue(CueType.EDIT_SAVED, text = "DELETED")
            }

            is Command.RestoreEvent -> {
                val cur = events[cmd.eventId] ?: return "Unknown event"
                if (!cur.deleted) return "Not deleted"
                tx.replace(cur, cur.copy(deleted = false))
                cues += Cue(CueType.EDIT_SAVED, text = "RESTORED")
            }

            is Command.InsertEvent -> {
                if (cmd.payload is GameCreated) return "Cannot insert game setup"
                tx.append(cmd.payload, period = cmd.period, clockMs = cmd.gameClockMs)
                cues += Cue(CueType.EDIT_SAVED, text = "EVENT ADDED")
            }

            Command.Undo, Command.Redo -> error("handled in dispatch")
        }
        return null
    }

    // ---------------------------------------------------------------------------------------
    // Undo / redo
    // ---------------------------------------------------------------------------------------

    fun undo(): Outcome {
        val t = history.popUndo() ?: return fail("Nothing to undo")
        val now = time.now()
        val written = mutableListOf<GameEvent>()
        for (c in t.changes.asReversed()) {
            val cur = events[c.after.id] ?: continue
            val restored = (c.before ?: cur.copy(deleted = true))
                .copy(rev = cur.rev + 1, origin = origin, editedAtWallMs = now.wallMs)
            events[restored.id] = restored
            written += restored
        }
        recompute()
        rearm()
        return Outcome(written, listOf(Cue(CueType.UNDO, text = "UNDO ${t.label}")))
    }

    fun redo(): Outcome {
        val t = history.popRedo() ?: return fail("Nothing to redo")
        val now = time.now()
        val written = mutableListOf<GameEvent>()
        for (c in t.changes) {
            val cur = events[c.after.id]
            val again = c.after.copy(rev = (cur?.rev ?: c.after.rev) + 1, origin = origin, editedAtWallMs = now.wallMs)
            events[again.id] = again
            written += again
        }
        recompute()
        rearm()
        return Outcome(written, listOf(Cue(CueType.REDO, text = "REDO ${t.label}")))
    }

    // ---------------------------------------------------------------------------------------
    // Time-driven transitions
    // ---------------------------------------------------------------------------------------

    /**
     * Called by the clock service (at [millisToNextDeadline] or on every UI frame). Emits the
     * buzzer, shot-clock horn, last-minute warning and timeout end. Not undoable.
     */
    fun tick(): Outcome {
        if (!state.created || state.finalized) return Outcome()
        val now = time.now()
        val written = mutableListOf<GameEvent>()
        val cues = mutableListOf<Cue>()
        val s = state

        if (s.clock.running) {
            val rem = s.clock.at(now)
            if (lastMinuteArmed && rem in 1..s.rules.lastMinuteWarningMs) {
                lastMinuteArmed = false
                cues += Cue(CueType.LAST_MINUTE, text = "LAST MINUTE")
            }

            // Shot clock violation (only while it is meaningful: before the game clock ends).
            val shotDeadline = s.shot.deadline()
            val clockDeadline = s.clock.deadline()
            if (s.rules.shotClockEnabled && !s.shotHeld && s.shot.running && s.shot.at(now) == 0L &&
                shotBuzzedFor != s.shot && shotDeadline != null && clockDeadline != null &&
                clockDeadline.elapsedSince(shotDeadline) > 0
            ) {
                shotBuzzedFor = s.shot
                cues += Cue(CueType.SHOT_CLOCK_EXPIRED, text = "SHOT CLOCK")
                if (shotExpiryStopsGame && rem > 0) {
                    val at = if (shotDeadline.boot == now.boot) shotDeadline else now
                    written += appendSystem(ClockStopped(), at)
                }
            }

            if (state.clock.running && rem == 0L) {
                val at = clockDeadline?.takeIf { it.boot == now.boot } ?: now
                written += appendSystem(ClockStopped(expired = true), at, clockMs = 0)
                val lastRegulationOrOt = s.period >= s.rules.regulationPeriods
                val gameOver = lastRegulationOrOt && !s.isTied
                cues += Cue(
                    if (gameOver) CueType.GAME_FINAL else CueType.PERIOD_END,
                    text = "END ${ClockFormat.periodShort(s.period, s.rules)}",
                )
            }
        }

        state.timeout?.let { t ->
            if (t.countdown.at(now) == 0L) {
                written += appendSystem(TimeoutEnded(t.side), now)
                cues += Cue(CueType.TIMEOUT_END, t.side, text = "TIMEOUT OVER")
            }
        }
        return Outcome(written, cues.sortedByDescending { it.type.priority })
    }

    /** Milliseconds until something in [tick] needs to happen, or null if nothing is pending. */
    fun millisToNextDeadline(): Long? {
        val now = time.now()
        val s = state
        val candidates = mutableListOf<Long>()
        if (s.clock.running) {
            val rem = s.clock.at(now)
            candidates += rem
            if (lastMinuteArmed && rem > s.rules.lastMinuteWarningMs) candidates += rem - s.rules.lastMinuteWarningMs
            if (s.rules.shotClockEnabled && s.shot.running && !s.shotHeld && shotBuzzedFor != s.shot) candidates += s.shot.at(now)
        }
        s.timeout?.let { candidates += it.countdown.at(now) }
        return candidates.minOrNull()?.coerceAtLeast(0)
    }

    // ---------------------------------------------------------------------------------------
    // Sync
    // ---------------------------------------------------------------------------------------

    /** Merge events from another device. Returns events that changed locally. */
    fun mergeRemote(remote: Collection<GameEvent>): Outcome {
        val relevant = remote.filter { it.gameId == gameId }
        val changed = relevant.filter { EventMerge.wins(it, events[it.id]) }
        if (changed.isEmpty()) return Outcome()
        changed.forEach { events[it.id] = it }
        nextSeq = maxOf(nextSeq, (changed.maxOf { it.seq }) + 1)
        recompute()
        rearm()
        return Outcome(changed)
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private fun appendSystem(payload: EventPayload, at: Stamp, clockMs: Long? = null): GameEvent {
        val e = GameEvent(
            id = newId(), gameId = gameId, seq = nextSeq++, at = at, period = state.period,
            gameClockMs = clockMs ?: state.clock.at(at), origin = origin, payload = payload,
        )
        events[e.id] = e
        state = Reducer.reduce(state, e)
        return e
    }

    private fun recompute() {
        state = Reducer.replay(gameId, events.values)
    }

    private fun rearm() {
        val rem = state.clock.at(time.now())
        if (rem > state.rules.lastMinuteWarningMs) lastMinuteArmed = true
    }

    private fun rollback(tx: Tx) {
        for (c in tx.changes.asReversed()) {
            if (c.before == null) events.remove(c.after.id) else events[c.before.id] = c.before
        }
        nextSeq = (events.values.maxOfOrNull { it.seq } ?: 0L) + 1
        recompute()
    }

    private fun fail(msg: String) = Outcome(cues = listOf(Cue(CueType.ERROR, text = msg.uppercase())), error = msg)

    private inner class Tx(val label: String) {
        val now: Stamp = time.now()
        val changes = mutableListOf<Change>()

        fun append(payload: EventPayload, period: Int? = null, clockMs: Long? = null): GameEvent {
            val e = GameEvent(
                id = newId(), gameId = gameId, seq = nextSeq++, at = now,
                period = period ?: state.period,
                gameClockMs = clockMs ?: state.clock.at(now),
                origin = origin, payload = payload,
            )
            events[e.id] = e
            changes += Change(null, e)
            state = Reducer.reduce(state, e)
            return e
        }

        fun replace(current: GameEvent, updated: GameEvent) {
            val v = updated.copy(rev = current.rev + 1, origin = origin, editedAtWallMs = now.wallMs)
            events[v.id] = v
            changes += Change(current, v)
            recompute()
        }

        fun toTransaction() = Transaction(label, changes.toList())
    }

    private fun labelFor(cmd: Command): String = when (cmd) {
        Command.ToggleClock -> if (state.clock.running) "STOP" else "START"
        Command.StartClock -> "START"
        Command.StopClock -> "STOP"
        is Command.AdjustClock -> "CLOCK ADJ"
        is Command.SetClock -> "CLOCK SET"
        is Command.ResetShotClock -> "SHOT RESET"
        Command.ToggleShotHold -> "SHOT HOLD"
        is Command.AdjustShotClock -> "SHOT ADJ"
        Command.NextPeriod -> "NEXT PERIOD"
        Command.PreviousPeriod -> "PREV PERIOD"
        Command.EndPeriod -> "END PERIOD"
        Command.FinalizeGame -> "FINAL"
        Command.ReopenGame -> "REOPEN"
        is Command.AddScore -> "+${cmd.points} ${state.team(cmd.side).info.shortName}"
        is Command.AddFoul -> "FOUL ${state.player(cmd.playerId)?.let { "#" + it.info.number } ?: state.team(cmd.side).info.shortName}"
        is Command.StartTimeout -> "TIMEOUT"
        Command.EndTimeout -> "END T/O"
        is Command.AdjustTimeouts -> "T/O ADJ"
        is Command.UpdateTeam -> "TEAM EDIT"
        is Command.UpsertPlayer -> "PLAYER #${cmd.player.number}"
        is Command.RemovePlayer -> "REMOVE PLAYER"
        is Command.UpdateRules -> "RULES"
        is Command.EditEvent -> "EDIT"
        is Command.AssignPlayer -> "ASSIGN"
        is Command.DeleteEvent -> "DELETE"
        is Command.RestoreEvent -> "RESTORE"
        is Command.InsertEvent -> "ADD EVENT"
        Command.Undo -> "UNDO"
        Command.Redo -> "REDO"
    }

    companion object {
        /** Convenience for tests/UI: is the game currently live (clock may run)? */
        fun isLive(s: GameState) = s.status == GameStatus.LIVE || s.status == GameStatus.PERIOD_BREAK
    }
}
