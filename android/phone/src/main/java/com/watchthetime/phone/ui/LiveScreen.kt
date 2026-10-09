package com.watchthetime.phone.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.SegmentText
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttIconButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.engine.isDestructive
import com.watchthetime.domain.event.Stamp
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState
import com.watchthetime.domain.state.GameStatus
import com.watchthetime.domain.state.PenaltyLevel
import com.watchthetime.domain.state.PeriodAction
import com.watchthetime.phone.Banner
import com.watchthetime.phone.Exporter
import com.watchthetime.phone.LiveGame
import com.watchthetime.phone.PendingAssign
import com.watchthetime.phone.container
import kotlinx.coroutines.delay

/** Dialogs the live screen can show (one at a time). */
sealed interface LiveDialog {
    data class Foul(val side: TeamSide) : LiveDialog
    data class ScoreFor(val side: TeamSide, val points: Int) : LiveDialog
    data class Assign(val eventId: String, val side: TeamSide, val points: Int) : LiveDialog
    data class Player(val playerId: String) : LiveDialog
    data class Team(val side: TeamSide) : LiveDialog
    data object Clock : LiveDialog
    data object Shot : LiveDialog
    data object Menu : LiveDialog
    data object ClockMode : LiveDialog
    data object Period : LiveDialog
}

/** Recomposes every frame while [active], so the clock is rendered from the drift-free model. */
@Composable
fun rememberNow(active: Boolean, key: Any?): Stamp {
    val c = LocalContext.current.container
    var now by remember { mutableStateOf(c.controller.now()) }
    LaunchedEffect(active, key) {
        now = c.controller.now()
        if (active) while (true) {
            withFrameMillis { }
            now = c.controller.now()
        }
    }
    return now
}

/**
 * The in-game scoreboard. Landscape only: the clock is the hero element; tap it to start or
 * stop, long-press it to edit the time. There is no start/stop button.
 */
@Composable
fun LiveScreen(
    gameId: String,
    onBack: () -> Unit,
    onLog: () -> Unit,
    onBox: () -> Unit,
    onSetup: () -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val live = rememberLiveGame(gameId)
    val settings by c.controller.settings.collectAsState()
    val banner by c.controller.banner.collectAsState()
    val assign by c.controller.pendingAssign.collectAsState()
    var dialog by remember { mutableStateOf<LiveDialog?>(null) }
    var confirm by remember { mutableStateOf<Pair<Command, String>?>(null) }
    var overlayHidden by remember { mutableStateOf(false) }

    // Landscape + immersive for the whole time this screen is open; screen stays awake for
    // the whole game (not only while the clock runs).
    LandscapeGameWindow(keepAwake = live?.state?.finalized != true)

    if (live == null) {
        Box(Modifier.fillMaxSize().background(Wtt.Black), contentAlignment = Alignment.Center) {
            Text("LOADING", style = MaterialTheme.typography.titleLarge, color = Wtt.Muted)
        }
        return
    }
    val s = live.state
    val active = s.clock.running || s.timeout != null
    val now = rememberNow(active, live.version)

    val send: (Command) -> Unit = { cmd ->
        if (settings.gestures.safeMode && cmd.isDestructive) confirm = cmd to confirmText(cmd, live)
        else c.controller.dispatch(cmd)
    }
    val ui = LiveUi(live, now, settings.display.showTenthsInLastMinute, send, { dialog = it })

    // End of a period that needs a decision (overtime or final): prompt once per transition.
    val action = s.nextPeriodAction()
    LaunchedEffect(s.status, s.period) {
        if (s.status == GameStatus.PERIOD_BREAK && action !is PeriodAction.Next) dialog = LiveDialog.Period
    }
    LaunchedEffect(s.finalized) { if (s.finalized) overlayHidden = false }

    val onClockTap: () -> Unit = {
        when {
            s.finalized -> overlayHidden = false
            s.status == GameStatus.PERIOD_BREAK -> dialog = LiveDialog.Period
            else -> c.controller.dispatch(Command.ToggleClock)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Wtt.Black).safeDrawingPadding()) {
        val scoreButtons = 3
        val spec = remember(maxWidth, maxHeight, scoreButtons) {
            LiveLayoutSpec.compute(maxWidth.value, maxHeight.value, scoreButtons)
        }
        if (maxWidth < maxHeight) {
            // Only while the device is still rotating (or in a portrait split-screen window).
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ROTATE TO LANDSCAPE", style = MaterialTheme.typography.headlineSmall, color = Wtt.Amber)
            }
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                SidePanel(ui, TeamSide.HOME, spec, Modifier.width(spec.sideWidth.dp).fillMaxHeight())
                CenterPanel(
                    ui, spec, banner, assign, settings.display.blinkWhenStopped, onClockTap,
                    onAssign = { a -> dialog = LiveDialog.Assign(a.eventId, a.score.side, a.score.points) },
                    onDismissAssign = { c.controller.dismissAssign() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                SidePanel(ui, TeamSide.AWAY, spec, Modifier.width(spec.sideWidth.dp).fillMaxHeight())
            }
            ActionStrip(ui, spec, scoreButtons)
        }
        if (s.finalized && !overlayHidden) {
            GameOverOverlay(s, onBox = onBox, onLog = onLog, onBack = onBack,
                onReopen = { send(Command.ReopenGame) }, onHide = { overlayHidden = true })
        }
    }

    // ---- Dialogs -------------------------------------------------------------------------
    when (val d = dialog) {
        null -> Unit
        is LiveDialog.Foul -> FoulDialog(s, d.side, settings.display.confirmFoulType, onDismiss = { dialog = null }) { pid, type ->
            c.controller.dispatch(Command.AddFoul(d.side, pid, type)); dialog = null
        }
        is LiveDialog.ScoreFor -> PlayerPickDialog(s, d.side, "+${d.points} ${s.team(d.side).info.shortName} · scorer", { dialog = null }) { pid ->
            c.controller.dispatch(Command.AddScore(d.side, d.points, pid)); dialog = null
        }
        is LiveDialog.Assign -> PlayerPickDialog(s, d.side, "+${d.points} ${s.team(d.side).info.shortName} · assign", { dialog = null }) { pid ->
            c.controller.dispatch(Command.AssignPlayer(d.eventId, pid)); dialog = null
        }
        is LiveDialog.Player -> PlayerDialog(s, d.playerId, send, onDismiss = { dialog = null })
        is LiveDialog.Team -> TeamDialog(ui, d.side, onDismiss = { dialog = null })
        LiveDialog.Clock -> ClockDialog(s, now, send, onDismiss = { dialog = null })
        LiveDialog.Shot -> ShotDialog(s, now, send, onDismiss = { dialog = null })
        LiveDialog.ClockMode -> ClockModeDialog(s, send, onDismiss = { dialog = null })
        LiveDialog.Period -> PeriodDialog(s, send, onDismiss = { dialog = null })
        LiveDialog.Menu -> MenuDialog(
            onDismiss = { dialog = null },
            items = listOf(
                "Clock mode · ${s.clockPolicy.summary}" to { dialog = LiveDialog.ClockMode },
                "Edit game clock & period" to { dialog = LiveDialog.Clock },
                "Event log" to onLog,
                "Box score" to onBox,
                "Teams, rosters, rules & clock" to onSetup,
                "Export CSV" to { Exporter.shareCsv(ctx, live.events, s) },
                "Export PDF" to { Exporter.sharePdf(ctx, live.events, s) },
                "Settings" to onSettings,
                "Leave game screen" to onBack,
            ),
        )
    }
    confirm?.let { (cmd, text) ->
        ConfirmDialog("Confirm", text, "Yes", onConfirm = { c.controller.dispatch(cmd) }, onDismiss = { confirm = null }, danger = cmd != Command.Redo)
    }
}

private fun confirmText(cmd: Command, live: LiveGame): String = when (cmd) {
    Command.Undo -> "Undo ${live.undoLabel ?: "last action"}?"
    Command.Redo -> "Redo ${live.redoLabel ?: "last action"}?"
    Command.PreviousPeriod -> if (live.state.finalized) "Reopen the game?" else "Go back to the previous period? The clock resets to a full period."
    Command.EndPeriod -> "End ${ClockFormat.periodShort(live.state.period, live.state.rules)} now? The clock goes to 0:00."
    Command.FinalizeGame -> "Mark the game as final?"
    is Command.SetClock -> "Set the game clock to ${ClockFormat.plain(cmd.remainingMs, true)}?"
    is Command.RemovePlayer -> "Remove ${live.state.player(cmd.playerId)?.info?.label ?: "player"} from the roster? Their stats stay in the log."
    is Command.DeleteEvent -> "Delete this event?"
    else -> "Are you sure?"
}

/** Everything the layout pieces need. */
class LiveUi(
    val live: LiveGame,
    val now: Stamp,
    val tenths: Boolean,
    val send: (Command) -> Unit,
    val open: (LiveDialog) -> Unit,
) {
    val s: GameState get() = live.state
}

// =========================================================================================
// Window: orientation lock, immersive mode, keep awake
// =========================================================================================

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun LandscapeGameWindow(keepAwake: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            bars?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previous
        }
    }
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun dpAsSp(dp: Float): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }

// =========================================================================================
// Side panels: team label, score, fouls/bonus, timeouts
// =========================================================================================

@Composable
private fun SidePanel(ui: LiveUi, side: TeamSide, spec: LiveLayoutSpec, modifier: Modifier) {
    val s = ui.s
    val t = s.team(side)
    val fouls = s.teamFouls(side)
    val bonus = s.inBonus(side)
    val tos = s.timeoutsRemaining(side)
    val home = side == TeamSide.HOME
    Column(modifier.padding(horizontal = LiveLayoutSpec.SIDE_PAD.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Top row: quarter indicator in the home corner, menu in the away corner.
        Row(Modifier.fillMaxWidth().height(LiveLayoutSpec.TOP_ROW.dp), verticalAlignment = Alignment.CenterVertically) {
            if (home) {
                Pill(if (s.finalized) "FINAL" else ClockFormat.periodShort(s.period, s.rules), Wtt.PanelHigh, Wtt.OffWhite)
                Spacer(Modifier.width(8.dp))
                TeamTag(t.info.shortName, t.info.colorArgb, Modifier.weight(1f))
            } else {
                TeamTag(t.info.shortName, t.info.colorArgb, Modifier.weight(1f))
                WttIconButton(WttIcons.Menu, "Game menu", { ui.open(LiveDialog.Menu) })
            }
        }
        Box(Modifier.fillMaxWidth().height((spec.scoreFontDp * LiveLayoutSpec.LINE).dp), contentAlignment = Alignment.Center) {
            SegmentText(pad3(t.score), dpAsSp(spec.scoreFontDp), color = Wtt.Amber, spoken = "${t.info.name} ${t.score}")
        }
        Row(
            Modifier.fillMaxWidth().height(LiveLayoutSpec.STATS_ROW.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Text("FOULS ", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted)
            SegmentText(fouls.toString().padStart(2, '!'), 18.sp,
                color = if (s.penalty(side) != PenaltyLevel.NONE) Wtt.Red else Wtt.OffWhite, spoken = "$fouls team fouls")
            Spacer(Modifier.width(8.dp))
            when (bonus) {
                PenaltyLevel.BONUS -> Pill("Bonus", Wtt.Amber, Wtt.Black)
                PenaltyLevel.DOUBLE_BONUS -> Pill("2× Bonus", Wtt.Red, Wtt.OffWhite)
                PenaltyLevel.NONE -> Unit
            }
        }
        Spacer(Modifier.weight(1f))
        WttButton(
            "T/O · $tos left", { ui.send(Command.StartTimeout(side)) }, Modifier.fillMaxWidth(),
            enabled = !s.finalized && s.timeout == null && tos > 0, minHeight = LiveLayoutSpec.MIN_TOUCH.dp,
        )
        Spacer(Modifier.height(4.dp))
    }
}

// =========================================================================================
// Center: banner, clock (tap / long-press), status, shot clock or timeout
// =========================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CenterPanel(
    ui: LiveUi,
    spec: LiveLayoutSpec,
    banner: Banner?,
    assign: PendingAssign?,
    blink: Boolean,
    onClockTap: () -> Unit,
    onAssign: (PendingAssign) -> Unit,
    onDismissAssign: () -> Unit,
    modifier: Modifier,
) {
    val s = ui.s
    val rem = s.clock.at(ui.now)
    val running = s.clock.running
    val stopped = !running && !s.finalized
    val color = when {
        s.finalized -> Wtt.OffWhite
        running -> Wtt.Amber
        else -> Wtt.Red
    }
    val pulse = rememberInfiniteTransition(label = "stopped")
    val alpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha",
    )

    Column(modifier.padding(horizontal = LiveLayoutSpec.CENTER_PAD.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(LiveLayoutSpec.BANNER.dp), contentAlignment = Alignment.Center) {
            if (assign != null) AssignChip(assign, ui.s, onAssign, onDismissAssign)
            else BannerText(banner)
        }
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = if (running) "Stop clock" else "Start clock",
                    onLongClickLabel = "Edit time",
                    onClick = onClockTap,
                    onLongClick = { ui.open(LiveDialog.Clock) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            SegmentText(
                ClockFormat.game(rem, ui.tenths, s.rules.lastMinuteWarningMs), dpAsSp(spec.clockFontDp), color = color,
                modifier = Modifier.graphicsLayer { this.alpha = if (stopped && blink) alpha else 1f },
                spoken = "Game clock ${ClockFormat.plain(rem, true)}, ${if (running) "running" else "stopped"}",
            )
        }
        Text(
            statusText(s), style = MaterialTheme.typography.labelMedium, color = if (running) Wtt.Amber else if (s.finalized) Wtt.Muted else Wtt.Red,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(LiveLayoutSpec.STATUS.dp),
        )
        Box(Modifier.fillMaxWidth().height(LiveLayoutSpec.SHOT_ROW.dp), contentAlignment = Alignment.Center) {
            val t = s.timeout
            when {
                t != null -> TimeoutBar(ui, t.side, t.countdown.at(ui.now))
                s.rules.shotClockEnabled -> ShotRow(ui, spec)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun statusText(s: GameState): String {
    val mode = s.clockPolicy.mode.label.uppercase()
    return when {
        s.finalized -> "FINAL · TAP CLOCK FOR SUMMARY"
        s.timeout != null -> "TIMEOUT · TAP CLOCK TO END IT AND START"
        s.status == GameStatus.PERIOD_BREAK -> when (val a = s.nextPeriodAction()) {
            is PeriodAction.Next -> "END ${ClockFormat.periodShort(s.period, s.rules)} · TAP CLOCK FOR ${ClockFormat.periodShort(a.period, s.rules)}"
            is PeriodAction.Overtime -> "TIED · TAP CLOCK FOR OVERTIME"
            PeriodAction.Final -> "GAME OVER · TAP CLOCK TO CONFIRM FINAL"
        }
        s.clock.running -> "RUNNING · $mode · TAP TO STOP"
        else -> "STOPPED · TAP TO START · HOLD TO EDIT"
    }
}

private fun bannerColor(t: CueType): Pair<Color, Color> = when (t) {
    CueType.PERIOD_END, CueType.GAME_FINAL, CueType.SHOT_CLOCK_EXPIRED, CueType.FOUL_OUT, CueType.ERROR -> Wtt.Red to Wtt.OffWhite
    CueType.BONUS, CueType.FOUL_WARNING, CueType.LAST_MINUTE, CueType.TIMEOUT_START, CueType.TIMEOUT_END,
    CueType.TIMEOUT_WARNING, CueType.INTERVAL_END, CueType.FOUL_FLAG, CueType.MODE_CHANGED -> Wtt.Amber to Wtt.Black
    else -> Wtt.PanelHigh to Wtt.OffWhite
}

/** Visual twin of every sound/vibration. Fixed slot, so the layout never jumps. */
@Composable
private fun BannerText(banner: Banner?) {
    var visible by remember { mutableStateOf<Banner?>(null) }
    LaunchedEffect(banner?.id) {
        visible = banner
        if (banner != null) {
            delay(if (banner.cue.type.priority >= 4) 5000 else 2500)
            visible = null
        }
    }
    val b = visible
    val (bg, fg) = b?.let { bannerColor(it.cue.type) } ?: (Color.Transparent to Wtt.Muted)
    Box(
        Modifier.fillMaxWidth().height(40.dp).background(bg).semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) Text(b.cue.text.uppercase(), style = MaterialTheme.typography.titleMedium, color = fg,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun AssignChip(a: PendingAssign, s: GameState, onAssign: (PendingAssign) -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(a.id) { delay(8000); onDismiss() }
    Row(Modifier.fillMaxWidth().background(Wtt.Panel).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("+${a.score.points} ${s.team(a.score.side).info.shortName}", style = MaterialTheme.typography.titleMedium,
            color = Wtt.OffWhite, modifier = Modifier.weight(1f), maxLines = 1)
        WttButton("Assign #", { onAssign(a) }, kind = ButtonKind.PRIMARY, minHeight = 48.dp)
        WttIconButton(WttIcons.Close, "Dismiss", onDismiss, tint = Wtt.Muted)
    }
}

@Composable
private fun ShotRow(ui: LiveUi, spec: LiveLayoutSpec) {
    val s = ui.s
    val shot = s.shot.at(ui.now)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WttButton("${s.rules.shotClockFullMs / 1000}", { ui.send(Command.ResetShotClock(false)) }, Modifier.widthIn(min = 56.dp),
            minHeight = 48.dp, textStyle = MaterialTheme.typography.titleLarge, enabled = !s.finalized)
        Box(
            Modifier.border(1.dp, Wtt.Line)
                .clickable(role = Role.Button, onClickLabel = "Shot clock controls") { ui.open(LiveDialog.Shot) }
                .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            SegmentText(ClockFormat.shot(shot), dpAsSp(spec.shotFontDp), color = if (s.shotHeld) Wtt.Muted else Wtt.Red,
                spoken = "Shot clock ${ClockFormat.spoken(ClockFormat.shot(shot))}")
        }
        if (s.rules.shotClockShortMs != s.rules.shotClockFullMs) {
            WttButton("${s.rules.shotClockShortMs / 1000}", { ui.send(Command.ResetShotClock(true)) }, Modifier.widthIn(min = 56.dp),
                minHeight = 48.dp, textStyle = MaterialTheme.typography.titleLarge, enabled = !s.finalized)
        }
    }
}

@Composable
private fun TimeoutBar(ui: LiveUi, side: TeamSide, remaining: Long) {
    Row(
        Modifier.fillMaxWidth().height(LiveLayoutSpec.SHOT_ROW.dp).background(Wtt.Amber).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TIMEOUT ${ui.s.team(side).info.shortName}", style = MaterialTheme.typography.titleMedium, color = Wtt.Black,
            maxLines = 1, modifier = Modifier.weight(1f))
        Text(ClockFormat.countdown(remaining).replace("!", ""), style = MaterialTheme.typography.headlineMedium, color = Wtt.Black)
        Spacer(Modifier.width(8.dp))
        WttButton("End", { ui.send(Command.EndTimeout) }, container = Wtt.Black, content = Wtt.Amber, minHeight = 48.dp)
    }
}

// =========================================================================================
// Bottom strip: per-team score/foul/more, undo/redo in the middle
// =========================================================================================

@Composable
private fun ActionStrip(ui: LiveUi, spec: LiveLayoutSpec, scoreButtons: Int) {
    Row(
        Modifier.fillMaxWidth().height(spec.stripHeight.dp).padding(horizontal = LiveLayoutSpec.STRIP_PAD.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamGroup(ui, TeamSide.HOME, spec, scoreButtons)
        Spacer(Modifier.weight(1f))
        WttIconButton(WttIcons.Undo, "Undo ${ui.live.undoLabel ?: ""}", { ui.send(Command.Undo) }, enabled = ui.live.canUndo)
        if (spec.showRedo) {
            Spacer(Modifier.width(spec.buttonGap.dp))
            WttIconButton(WttIcons.Redo, "Redo ${ui.live.redoLabel ?: ""}", { ui.send(Command.Redo) }, enabled = ui.live.canRedo)
        }
        Spacer(Modifier.weight(1f))
        TeamGroup(ui, TeamSide.AWAY, spec, scoreButtons)
    }
}

@Composable
private fun TeamGroup(ui: LiveUi, side: TeamSide, spec: LiveLayoutSpec, scoreButtons: Int) {
    val s = ui.s
    val enabled = !s.finalized
    val short = s.team(side).info.shortName
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(spec.buttonGap.dp)) {
            for (pts in 1..scoreButtons) {
                StripButton("+$pts", "Add $pts to $short", spec, enabled, onLongClickLabel = "Pick scorer",
                    onLongClick = { ui.open(LiveDialog.ScoreFor(side, pts)) }) { ui.send(Command.AddScore(side, pts)) }
            }
            StripButton("FOUL", "Foul $short", spec, enabled, content = Wtt.Amber) { ui.open(LiveDialog.Foul(side)) }
            StripButton("•••", "$short timeouts and players", spec, true) { ui.open(LiveDialog.Team(side)) }
        }
        Box(Modifier.width(spec.teamGroupWidth.dp).height(4.dp).background(Wtt.argb(s.team(side).info.colorArgb)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripButton(
    text: String,
    label: String,
    spec: LiveLayoutSpec,
    enabled: Boolean,
    content: Color = Wtt.OffWhite,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(spec.buttonWidth.dp, LiveLayoutSpec.MIN_TOUCH.dp)
            .background(if (enabled) Wtt.PanelHigh else Wtt.Panel)
            .combinedClickable(
                enabled = enabled, role = Role.Button, onClickLabel = label,
                onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (text.startsWith("+")) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            color = if (enabled) content else Wtt.Disabled, maxLines = 1, softWrap = false)
    }
}

// =========================================================================================
// Dialogs and overlays
// =========================================================================================

/** Team "•••": timeouts, players (points/fouls) and per-player actions. */
@Composable
private fun TeamDialog(ui: LiveUi, side: TeamSide, onDismiss: () -> Unit) {
    val s = ui.s
    val t = s.team(side)
    val tos = s.timeoutsRemaining(side)
    WttDialog("${t.info.shortName} · ${t.info.name}", onDismiss) {
        Text("${t.score} PTS · ${s.teamFouls(side)} team fouls · $tos timeouts left" +
            when (s.inBonus(side)) { PenaltyLevel.BONUS -> " · BONUS"; PenaltyLevel.DOUBLE_BONUS -> " · DOUBLE BONUS"; else -> "" },
            style = MaterialTheme.typography.titleMedium, color = Wtt.OffWhite, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        WttButton("Call timeout · $tos left", { ui.send(Command.StartTimeout(side)); onDismiss() }, Modifier.fillMaxWidth(),
            kind = ButtonKind.PRIMARY, icon = WttIcons.Timeout, enabled = !s.finalized && s.timeout == null && tos > 0, minHeight = 52.dp)
        SectionLabel("Players · tap for score, fouls, edit")
        JerseyGrid(s, side, onPick = { ui.open(LiveDialog.Player(it.id)) })
        Text("Tip: long-press +1 / +2 / +3 to pick the scorer first.", style = MaterialTheme.typography.bodySmall,
            color = Wtt.Muted, modifier = Modifier.padding(vertical = 8.dp))
    }
}

/** Next period / overtime prompt / confirm final. */
@Composable
private fun PeriodDialog(s: GameState, send: (Command) -> Unit, onDismiss: () -> Unit) {
    val home = s.team(TeamSide.HOME)
    val away = s.team(TeamSide.AWAY)
    val score = "${home.info.shortName} ${home.score} – ${away.score} ${away.info.shortName}"
    val (title, body, button) = when (val a = s.nextPeriodAction()) {
        is PeriodAction.Next -> Triple("End of ${ClockFormat.periodShort(s.period, s.rules)}", score,
            "Start ${ClockFormat.periodShort(a.period, s.rules)}")
        is PeriodAction.Overtime -> Triple("Tied · overtime", "$score\nOvertime ${a.period - s.rules.regulationPeriods} · " +
            "${s.rules.overtimeLengthMs / 60_000} min", "Start overtime")
        PeriodAction.Final -> Triple("Game over", score, "Confirm final")
    }
    WttDialog(title, onDismiss, buttons = {
        WttButton("Not yet", onDismiss, kind = ButtonKind.GHOST)
        WttButton(button, { send(Command.NextPeriod); onDismiss() }, kind = ButtonKind.PRIMARY, minHeight = 52.dp)
    }) {
        Text(body, style = MaterialTheme.typography.headlineSmall, color = Wtt.OffWhite, modifier = Modifier.padding(vertical = 12.dp))
        if (s.status != GameStatus.PERIOD_BREAK) {
            Text("The period clock still shows ${ClockFormat.plain(s.clock.baseMs, true)}.", style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
        }
    }
}

@Composable
private fun GameOverOverlay(
    s: GameState,
    onBox: () -> Unit,
    onLog: () -> Unit,
    onBack: () -> Unit,
    onReopen: () -> Unit,
    onHide: () -> Unit,
) {
    val home = s.team(TeamSide.HOME)
    val away = s.team(TeamSide.AWAY)
    Box(Modifier.fillMaxSize().background(Wtt.Black).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FINAL", style = MaterialTheme.typography.displaySmall, color = Wtt.Red)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                TeamTag(home.info.shortName, home.info.colorArgb)
                Spacer(Modifier.width(12.dp))
                SegmentText(pad3(home.score), 64.sp, color = Wtt.Amber, spoken = "${home.info.name} ${home.score}")
                Text("  –  ", style = MaterialTheme.typography.displaySmall, color = Wtt.Muted)
                SegmentText(pad3(away.score), 64.sp, color = Wtt.Amber, spoken = "${away.info.name} ${away.score}")
                Spacer(Modifier.width(12.dp))
                TeamTag(away.info.shortName, away.info.colorArgb)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WttButton("Box score", onBox, kind = ButtonKind.PRIMARY, icon = WttIcons.Table, minHeight = 52.dp)
                WttButton("Event log", onLog, icon = WttIcons.Log, minHeight = 52.dp)
                WttButton("Scoreboard", onHide, kind = ButtonKind.GHOST, minHeight = 52.dp)
                WttButton("Reopen", onReopen, kind = ButtonKind.GHOST, minHeight = 52.dp)
                WttButton("Games", onBack, kind = ButtonKind.GHOST, icon = WttIcons.Back, minHeight = 52.dp)
            }
        }
    }
}

@Composable
fun MenuDialog(onDismiss: () -> Unit, items: List<Pair<String, () -> Unit>>) {
    WttDialog("Game", onDismiss) {
        for ((label, action) in items) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clickable { onDismiss(); action() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label.uppercase(), style = MaterialTheme.typography.titleMedium, color = Wtt.OffWhite, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(WttIcons.ChevronRight, null, tint = Wtt.Muted, modifier = Modifier.size(20.dp))
            }
            HRule()
        }
    }
}
