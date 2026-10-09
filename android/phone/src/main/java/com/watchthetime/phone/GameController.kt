package com.watchthetime.phone

import android.content.Context
import com.watchthetime.data.GameRepository
import com.watchthetime.data.SettingsRepository
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.Cue
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.engine.GameSession
import com.watchthetime.domain.engine.Outcome
import com.watchthetime.domain.engine.randomId
import com.watchthetime.domain.event.GameEvent
import com.watchthetime.domain.event.Score
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.settings.AppSettings
import com.watchthetime.domain.state.GameState
import com.watchthetime.feedback.FeedbackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Snapshot of the open game for the UI. [version] bumps on every change. */
data class LiveGame(
    val gameId: String,
    val state: GameState,
    val events: List<GameEvent>,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val undoLabel: String?,
    val redoLabel: String?,
    val version: Long,
)

/** Visual twin of every sound/vibration (accessibility: nothing is sound-only). */
data class Banner(val cue: Cue, val id: Long)

/** Score that can still be attributed to a player ("ASSIGN #" chip). */
data class PendingAssign(val eventId: String, val score: Score, val id: Long)

/**
 * Owns the single [GameSession] of the open game. All access happens on the main thread;
 * persistence is serialised through one writer coroutine on IO, so events hit the database in
 * the order they were produced.
 */
class GameController(
    private val context: Context,
    private val repo: GameRepository,
    settingsRepo: SettingsRepository,
    private val feedback: FeedbackPlayer,
    private val time: AndroidTime,
    private val scope: CoroutineScope,
) {
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private var session: GameSession? = null
    private var version = 0L
    private var bannerSeq = 0L
    private var tickJob: Job? = null

    private val _live = MutableStateFlow<LiveGame?>(null)
    val live: StateFlow<LiveGame?> = _live.asStateFlow()

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _assign = MutableStateFlow<PendingAssign?>(null)
    val pendingAssign: StateFlow<PendingAssign?> = _assign.asStateFlow()

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) { for (w in writes) runCatching { w() } }
    }

    val openGameId: String? get() = session?.gameId

    fun now() = time.now()

    // ---------------------------------------------------------------------------------------

    /** Loads [gameId] (no-op if already open). */
    suspend fun open(gameId: String) {
        if (session?.gameId == gameId) return
        val events = withContext(Dispatchers.IO) { repo.loadEvents(gameId) }
        close()
        session = GameSession(gameId, events, time, ORIGIN, shotExpiryStopsGame = settings.value.game.shotExpiryStopsGame)
        _banner.value = null
        _assign.value = null
        publish()
        startTicking()
    }

    fun close() {
        tickJob?.cancel()
        tickJob = null
        session = null
        _live.value = null
        _assign.value = null
        ClockService.update(context, null)
    }

    /** Creates and opens a new game; returns its id. */
    fun create(
        rules: Rules,
        home: TeamInfo,
        away: TeamInfo,
        roster: List<PlayerInfo>,
        title: String,
        clockPolicy: ClockPolicy = ClockPolicy.NEW_GAME,
    ): String {
        close()
        val id = randomId()
        val s = GameSession(id, emptyList(), time, ORIGIN, shotExpiryStopsGame = settings.value.game.shotExpiryStopsGame)
        val out = s.create(rules, home, away, roster, title, clockPolicy)
        session = s
        persist(out)
        publish()
        startTicking()
        return id
    }

    fun dispatch(cmd: Command): Outcome {
        val s = session ?: return Outcome(error = "No game open")
        s.shotExpiryStopsGame = settings.value.game.shotExpiryStopsGame
        val out = s.dispatch(cmd)
        handle(out)
        if (out.ok) {
            when (cmd) {
                is Command.AddScore -> {
                    val ev = out.changed.firstOrNull { it.payload is Score }
                    if (ev != null && cmd.playerId == null && settings.value.display.promptScorer &&
                        s.state.roster(cmd.side).isNotEmpty()
                    ) _assign.value = PendingAssign(ev.id, ev.payload as Score, ++bannerSeq)
                }
                is Command.AssignPlayer -> if (_assign.value?.eventId == cmd.eventId) _assign.value = null
                Command.Undo, Command.Redo -> {
                    val a = _assign.value
                    if (a != null && s.event(a.eventId)?.deleted != false) _assign.value = null
                }
                else -> Unit
            }
        }
        return out
    }

    fun dismissAssign() { _assign.value = null }

    fun previewCue(type: CueType) = feedback.preview(type, settings.value.feedback)

    fun outputRoute() = feedback.outputRoute()

    suspend fun deleteGame(gameId: String) {
        if (session?.gameId == gameId) close()
        withContext(Dispatchers.IO) { repo.deleteGame(gameId) }
    }

    // ---------------------------------------------------------------------------------------

    private fun handle(out: Outcome) {
        persist(out)
        if (out.cues.isNotEmpty()) {
            feedback.play(out.cues, settings.value.feedback)
            val top = out.cues.maxBy { it.type.priority }
            _banner.value = Banner(top, ++bannerSeq)
        }
        if (out.changed.isNotEmpty()) publish()
    }

    private fun persist(out: Outcome) {
        val s = session ?: return
        if (out.changed.isEmpty()) return
        val changed = out.changed
        val state = s.state
        val now = time.now()
        val clockMs = state.clock.at(now)
        writes.trySend {
            repo.saveEvents(changed)
            repo.updateIndex(state, clockMs, now.wallMs)
        }
    }

    private fun publish() {
        val s = session ?: return
        val live = LiveGame(
            gameId = s.gameId, state = s.state, events = s.allEvents,
            canUndo = s.canUndo, canRedo = s.canRedo, undoLabel = s.undoLabel, redoLabel = s.redoLabel,
            version = ++version,
        )
        _live.value = live
        ClockService.update(context, live)
    }

    /**
     * Drives buzzer / shot clock / last-minute / timeout end. Sleeps until the next deadline
     * (capped so a reboot or clock edit is noticed quickly).
     */
    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val s = session ?: break
                val wait = s.millisToNextDeadline()
                delay(if (wait == null) 250 else wait.coerceIn(4, 250))
                val out = s.tick()
                if (out.changed.isNotEmpty() || out.cues.isNotEmpty()) handle(out)
            }
        }
    }

    companion object {
        const val ORIGIN = "phone"
    }
}
