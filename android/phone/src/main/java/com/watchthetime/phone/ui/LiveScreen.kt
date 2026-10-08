package com.watchthetime.phone.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.HRule
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
import com.watchthetime.phone.container
import kotlinx.coroutines.delay

/** Dialogs the live screen can show (one at a time). */
sealed interface LiveDialog {
    data class Foul(val side: TeamSide) : LiveDialog
    data class ScoreFor(val side: TeamSide, val points: Int) : LiveDialog
    data class Assign(val eventId: String, val side: TeamSide, val points: Int) : LiveDialog
    data class Player(val playerId: String) : LiveDialog
    data object Clock : LiveDialog
    data object Shot : LiveDialog
    data object Menu : LiveDialog
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

    if (live == null) {
        WttScreen("Loading", onBack) {}
        return
    }
    val s = live.state
    val active = s.clock.running || s.timeout != null
    val now = rememberNow(active, live.version)

    // Keep the display on while the clock or a timeout runs.
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    val send: (Command) -> Unit = { cmd ->
        if (settings.gestures.safeMode && cmd.isDestructive) confirm = cmd to confirmText(cmd, live)
        else c.controller.dispatch(cmd)
    }

    val ui = LiveUi(live, now, settings.display.showTenthsInLastMinute, send, { dialog = it })

    WttScreen(
        title = if (s.finalized) "Final" else ClockFormat.periodLong(s.period, s.rules),
        subtitle = s.title.ifBlank { "${s.team(TeamSide.HOME).info.name} vs ${s.team(TeamSide.AWAY).info.name}" },
        onBack = onBack,
        actions = {
            WttIconButton(WttIcons.Undo, "Undo ${live.undoLabel ?: ""}", { send(Command.Undo) }, enabled = live.canUndo)
            WttIconButton(WttIcons.Redo, "Redo ${live.redoLabel ?: ""}", { send(Command.Redo) }, enabled = live.canRedo)
            WttIconButton(WttIcons.Log, "Event log", onLog)
            WttIconButton(WttIcons.Menu, "More", { dialog = LiveDialog.Menu })
        },
    ) {
        BannerStrip(banner)
        assign?.let { a ->
            AssignChip(a.score.points, s.team(a.score.side).info.shortName,
                onAssign = { dialog = LiveDialog.Assign(a.eventId, a.score.side, a.score.points) },
                onDismiss = { c.controller.dismissAssign() }, key = a.id)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth > maxHeight) LandscapeLayout(ui, onBox) else PortraitLayout(ui, onBox, maxWidth)
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
        LiveDialog.Clock -> ClockDialog(s, now, send, onDismiss = { dialog = null })
        LiveDialog.Shot -> ShotDialog(s, now, send, onDismiss = { dialog = null })
        LiveDialog.Menu -> MenuDialog(
            onDismiss = { dialog = null },
            items = listOf(
                "Event log" to onLog,
                "Box score" to onBox,
                "Teams, rosters & rules" to onSetup,
                "Export CSV" to { Exporter.shareCsv(ctx, live.events, s) },
                "Export PDF" to { Exporter.sharePdf(ctx, live.events, s) },
                "Settings" to onSettings,
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
// Layouts
// =========================================================================================

@Composable
private fun PortraitLayout(ui: LiveUi, onBox: () -> Unit, width: Dp) {
    val s = ui.s
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(IntrinsicHeightFix)) {
            ScoreCard(ui, TeamSide.HOME, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Wtt.Line))
            ScoreCard(ui, TeamSide.AWAY, Modifier.weight(1f))
        }
        HRule()
        ClockBlock(ui, clockSize = (width.value / 4.6f).coerceIn(48f, 110f).sp)
        TimeoutPanel(ui)
        MainButton(ui, onBox, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TeamControls(ui, TeamSide.HOME, Modifier.weight(1f))
            TeamControls(ui, TeamSide.AWAY, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        HRule()
        Row(Modifier.fillMaxWidth()) {
            PlayerList(ui, TeamSide.HOME, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(1.dp))
            PlayerList(ui, TeamSide.AWAY, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val IntrinsicHeightFix = 168.dp

@Composable
private fun LandscapeLayout(ui: LiveUi, onBox: () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            ScoreCard(ui, TeamSide.HOME, Modifier.fillMaxWidth().height(IntrinsicHeightFix))
            TeamControls(ui, TeamSide.HOME, Modifier.padding(8.dp))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Wtt.Line))
        Column(Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            ClockBlock(ui, clockSize = 84.sp)
            TimeoutPanel(ui)
            MainButton(ui, onBox, Modifier.fillMaxWidth().padding(8.dp))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Wtt.Line))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            ScoreCard(ui, TeamSide.AWAY, Modifier.fillMaxWidth().height(IntrinsicHeightFix))
            TeamControls(ui, TeamSide.AWAY, Modifier.padding(8.dp))
        }
    }
}

// =========================================================================================
// Pieces
// =========================================================================================

private fun bannerColor(t: CueType): Pair<Color, Color> = when (t) {
    CueType.PERIOD_END, CueType.GAME_FINAL, CueType.SHOT_CLOCK_EXPIRED, CueType.FOUL_OUT, CueType.ERROR -> Wtt.Red to Wtt.OffWhite
    CueType.BONUS, CueType.FOUL_WARNING, CueType.LAST_MINUTE, CueType.TIMEOUT_START, CueType.TIMEOUT_END, CueType.FOUL_FLAG -> Wtt.Amber to Wtt.Black
    else -> Wtt.PanelHigh to Wtt.OffWhite
}

/** Visual twin of every sound/vibration. Fixed height so the layout never jumps. */
@Composable
private fun BannerStrip(banner: Banner?) {
    var visible by remember { mutableStateOf<Banner?>(null) }
    LaunchedEffect(banner?.id) {
        visible = banner
        if (banner != null) {
            delay(if (banner.cue.type.priority >= 4) 5000 else 2500)
            visible = null
        }
    }
    val b = visible
    val (bg, fg) = b?.let { bannerColor(it.cue.type) } ?: (Wtt.Black to Wtt.Muted)
    Box(
        Modifier.fillMaxWidth().height(36.dp).background(bg).semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) Text(b.cue.text.uppercase(), style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HRule()
}

@Composable
private fun AssignChip(points: Int, team: String, onAssign: () -> Unit, onDismiss: () -> Unit, key: Long) {
    LaunchedEffect(key) { delay(8000); onDismiss() }
    Row(Modifier.fillMaxWidth().background(Wtt.Panel).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("+$points $team", style = MaterialTheme.typography.titleMedium, color = Wtt.OffWhite, modifier = Modifier.weight(1f))
        WttButton("Assign #", onAssign, kind = ButtonKind.PRIMARY, minHeight = 40.dp)
        WttIconButton(WttIcons.Close, "Dismiss", onDismiss, tint = Wtt.Muted)
    }
    HRule()
}

@Composable
private fun ScoreCard(ui: LiveUi, side: TeamSide, modifier: Modifier) {
    val s = ui.s
    val t = s.team(side)
    val onTeam = Wtt.onTeam(t.info.colorArgb)
    val bonus = s.inBonus(side)
    val fouls = s.teamFouls(side)
    val tos = s.timeoutsRemaining(side)
    Column(modifier.background(Wtt.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Wtt.argb(t.info.colorArgb)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.info.shortName, style = MaterialTheme.typography.titleLarge, color = onTeam, modifier = Modifier.weight(1f), maxLines = 1)
            Text(if (side == TeamSide.HOME) "HOME" else "AWAY", style = MaterialTheme.typography.labelSmall, color = onTeam)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            SegmentText(pad3(t.score), 60.sp, color = Wtt.Amber, spoken = "${t.info.name} ${t.score}")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("FOULS", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted)
            Spacer(Modifier.width(4.dp))
            SegmentText(fouls.toString().padStart(2, '!'), 18.sp, color = if (s.penalty(side) != PenaltyLevel.NONE) Wtt.Red else Wtt.OffWhite, spoken = "$fouls team fouls")
            Spacer(Modifier.weight(1f))
            Text("T/O", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted)
            Spacer(Modifier.width(4.dp))
            SegmentText(tos.toString(), 18.sp, color = Wtt.OffWhite, spoken = "$tos timeouts left")
        }
        Box(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 10.dp)) {
            when (bonus) {
                PenaltyLevel.BONUS -> Pill("Bonus", Wtt.Amber, Wtt.Black)
                PenaltyLevel.DOUBLE_BONUS -> Pill("Double bonus", Wtt.Red, Wtt.OffWhite)
                PenaltyLevel.NONE -> Unit
            }
        }
    }
}

@Composable
private fun ClockBlock(ui: LiveUi, clockSize: androidx.compose.ui.unit.TextUnit) {
    val s = ui.s
    val rem = s.clock.at(ui.now)
    val color = when {
        s.finalized -> Wtt.OffWhite
        rem == 0L -> Wtt.Red
        s.clock.running -> Wtt.Amber
        else -> Wtt.OffWhite
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.clickable(role = Role.Button, onClickLabel = "Adjust clock and period") { ui.open(LiveDialog.Clock) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            SegmentText(
                ClockFormat.game(rem, ui.tenths, s.rules.lastMinuteWarningMs), clockSize, color = color,
                spoken = "Game clock ${ClockFormat.plain(rem, true)}",
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(ClockFormat.periodShort(s.period, s.rules), Wtt.PanelHigh, Wtt.OffWhite)
            if (s.rules.shotClockEnabled) {
                val shot = s.shot.at(ui.now)
                Box(
                    Modifier.border(1.dp, Wtt.Line).clickable(role = Role.Button, onClickLabel = "Adjust shot clock") { ui.open(LiveDialog.Shot) }
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    SegmentText(
                        ClockFormat.shot(shot), 40.sp,
                        color = if (s.shotHeld) Wtt.Muted else Wtt.Red,
                        spoken = "Shot clock ${ClockFormat.spoken(ClockFormat.shot(shot))}",
                    )
                }
                WttButton("${s.rules.shotClockFullMs / 1000}", { ui.send(Command.ResetShotClock(false)) }, minHeight = 52.dp,
                    textStyle = MaterialTheme.typography.headlineSmall, icon = WttIcons.Reset, enabled = !s.finalized)
                WttButton("${s.rules.shotClockShortMs / 1000}", { ui.send(Command.ResetShotClock(true)) }, minHeight = 52.dp,
                    textStyle = MaterialTheme.typography.headlineSmall, enabled = !s.finalized)
            }
        }
        if (s.shotHeld) Text("SHOT CLOCK HOLD", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted)
    }
}

@Composable
private fun TimeoutPanel(ui: LiveUi) {
    val t = ui.s.timeout ?: return
    val team = ui.s.team(t.side).info
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(Wtt.Amber).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("TIMEOUT", style = MaterialTheme.typography.labelMedium, color = Wtt.Black)
            Text(team.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = Wtt.Black, maxLines = 1)
        }
        Text(ClockFormat.countdown(t.countdown.at(ui.now)).replace("!", ""), style = MaterialTheme.typography.displaySmall, color = Wtt.Black)
        Spacer(Modifier.width(12.dp))
        WttButton("End", { ui.send(Command.EndTimeout) }, container = Wtt.Black, content = Wtt.Amber, minHeight = 48.dp)
    }
}

@Composable
private fun MainButton(ui: LiveUi, onBox: () -> Unit, modifier: Modifier) {
    val s = ui.s
    val style = MaterialTheme.typography.headlineMedium
    when {
        s.finalized -> WttButton("Final · box score", onBox, modifier, kind = ButtonKind.SECONDARY, icon = WttIcons.Table, minHeight = 72.dp, textStyle = style)
        s.status == GameStatus.PERIOD_BREAK -> {
            val label = when (val a = s.nextPeriodAction()) {
                is PeriodAction.Next -> "Go to ${ClockFormat.periodShort(a.period, s.rules)}"
                is PeriodAction.Overtime -> "Go to overtime ${a.period - s.rules.regulationPeriods}"
                PeriodAction.Final -> "End game · final"
            }
            WttButton(label, { ui.send(Command.NextPeriod) }, modifier, kind = ButtonKind.PRIMARY, minHeight = 72.dp, textStyle = style)
        }
        s.clock.running -> WttButton("Stop", { ui.send(Command.StopClock) }, modifier, kind = ButtonKind.DANGER, icon = WttIcons.Pause, minHeight = 72.dp, textStyle = style)
        else -> WttButton(if (s.timeout != null) "End timeout · start" else "Start", { ui.send(Command.StartClock) }, modifier,
            kind = ButtonKind.PRIMARY, icon = WttIcons.Play, minHeight = 72.dp, textStyle = style)
    }
}

@Composable
private fun TeamControls(ui: LiveUi, side: TeamSide, modifier: Modifier) {
    val s = ui.s
    val enabled = !s.finalized
    val big = MaterialTheme.typography.headlineSmall
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(s.team(side).info.shortName, style = MaterialTheme.typography.labelMedium, color = Wtt.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (pts in 1..3) {
                WttButton(
                    "+$pts", { ui.send(Command.AddScore(side, pts)) }, Modifier.weight(1f), enabled = enabled, minHeight = 60.dp, textStyle = big,
                    onLongClick = { ui.open(LiveDialog.ScoreFor(side, pts)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WttButton("Foul", { ui.open(LiveDialog.Foul(side)) }, Modifier.weight(1f), icon = WttIcons.Whistle, enabled = enabled, minHeight = 52.dp)
            WttButton(
                "T/O", { ui.send(Command.StartTimeout(side)) }, Modifier.weight(1f), icon = WttIcons.Timeout,
                enabled = enabled && s.timeout == null && s.timeoutsRemaining(side) > 0, minHeight = 52.dp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerList(ui: LiveUi, side: TeamSide, modifier: Modifier) {
    val s = ui.s
    val roster = s.roster(side)
    Column(modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(s.team(side).info.shortName, style = MaterialTheme.typography.labelMedium, color = Wtt.Muted, modifier = Modifier.weight(1f))
            Text("PTS", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
            Text("PF", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        }
        if (roster.isEmpty()) Text("No roster", style = MaterialTheme.typography.bodySmall, color = Wtt.Disabled)
        for (p in roster) {
            val out = p.disqualified(s.rules)
            val trouble = p.inFoulTrouble(s.rules)
            Row(
                Modifier.fillMaxWidth().height(40.dp).clickable(onClickLabel = "Player actions") { ui.open(LiveDialog.Player(p.id)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(30.dp)) {
                    Text(p.info.number, style = MaterialTheme.typography.titleMedium, color = if (out) Wtt.Red else Wtt.OffWhite)
                }
                Text(p.info.name.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = if (out) Wtt.Red else Wtt.OffWhite,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                for ((type, n) in p.flagBadges()) Pill(type.code + if (n > 1) n else "", Wtt.Red, Wtt.OffWhite, Modifier.padding(start = 2.dp))
                Text("${p.points}", style = MaterialTheme.typography.titleSmall, color = Wtt.OffWhite, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                Text(
                    "${p.countedFouls(s.rules)}", style = MaterialTheme.typography.titleSmall,
                    color = when { out -> Wtt.Red; trouble -> Wtt.Amber; else -> Wtt.OffWhite },
                    modifier = Modifier.width(28.dp), textAlign = TextAlign.End,
                )
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
                Text(label.uppercase(), style = MaterialTheme.typography.titleMedium, color = Wtt.OffWhite, modifier = Modifier.weight(1f))
                Icon(WttIcons.ChevronRight, null, tint = Wtt.Muted, modifier = Modifier.size(20.dp))
            }
            HRule()
        }
    }
}
