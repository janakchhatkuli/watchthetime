package com.watchthetime.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.EmptyNote
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.Stepper
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttChip
import com.watchthetime.brand.WttIcons
import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.EventText
import com.watchthetime.domain.engine.isDestructive
import com.watchthetime.domain.event.EventPayload
import com.watchthetime.domain.event.Foul
import com.watchthetime.domain.event.GameCreated
import com.watchthetime.domain.event.GameEvent
import com.watchthetime.domain.event.Score
import com.watchthetime.domain.event.TimeoutStarted
import com.watchthetime.domain.event.TimeoutsAdjusted
import com.watchthetime.domain.event.kind
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState
import com.watchthetime.phone.container

private val FILTERS = listOf("ALL", "SCORE", "FOUL", "CLOCK", "PERIOD", "TIMEOUT", "SHOT", "ROSTER", "DELETED")

@Composable
fun LogScreen(gameId: String, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val live = rememberLiveGame(gameId)
    val settings by c.controller.settings.collectAsState()
    var filter by remember { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<GameEvent?>(null) }
    var inserting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Command?>(null) }

    if (live == null) { WttScreen("Event log", onBack) {}; return }
    val s = live.state
    val send: (Command) -> Unit = { cmd ->
        if (settings.gestures.safeMode && cmd.isDestructive) confirm = cmd else c.controller.dispatch(cmd)
    }

    val events = EventText.logOrder(live.events).filter { e ->
        when (filter) {
            "ALL" -> true
            "DELETED" -> e.deleted
            "ROSTER" -> e.payload.kind == "ROSTER" || e.payload.kind == "TEAM" || e.payload.kind == "GAME" || e.payload.kind == "RULES"
            else -> e.payload.kind == filter
        }
    }

    WttScreen("Event log", onBack, subtitle = "${live.events.count { !it.deleted }} events · tap to edit") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in FILTERS) WttChip(f, filter == f, { filter = f })
        }
        HRule()
        Box(Modifier.weight(1f)) {
            if (events.isEmpty()) EmptyNote("Nothing here.")
            LazyColumn(Modifier.fillMaxSize()) {
                items(events, key = { it.id }) { e ->
                    LogRow(e, s) { editing = e }
                    HRule()
                }
            }
        }
        HRule()
        WttButton("Add missed event", { inserting = true }, kind = ButtonKind.PRIMARY, icon = WttIcons.Plus,
            modifier = Modifier.fillMaxWidth().padding(12.dp), minHeight = 52.dp)
    }

    editing?.let { e ->
        val current = live.events.firstOrNull { it.id == e.id } ?: e
        EventEditDialog(
            s, current, onDismiss = { editing = null },
            onSave = { payload, period, clock -> c.controller.dispatch(Command.EditEvent(current.id, payload, period, clock)); editing = null },
            onDelete = { send(Command.DeleteEvent(current.id)); editing = null },
            onRestore = { c.controller.dispatch(Command.RestoreEvent(current.id)); editing = null },
        )
    }
    if (inserting) InsertEventDialog(s, onDismiss = { inserting = false }) { payload, period, clock ->
        c.controller.dispatch(Command.InsertEvent(payload, period, clock)); inserting = false
    }
    confirm?.let { cmd ->
        ConfirmDialog("Delete event?", "The event is kept as deleted and can be restored from the DELETED filter.", "Delete",
            onConfirm = { c.controller.dispatch(cmd) }, onDismiss = { confirm = null })
    }
}

@Composable
private fun LogRow(e: GameEvent, s: GameState, onClick: () -> Unit) {
    val side = when (val p = e.payload) { is Score -> p.side; is Foul -> p.side; is TimeoutStarted -> p.side; else -> null }
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Edit event", onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(72.dp)) {
            Text(ClockFormat.periodShort(e.period, s.rules), style = MaterialTheme.typography.labelMedium, color = Wtt.Muted)
            Text(ClockFormat.plain(e.gameClockMs, true), style = MaterialTheme.typography.titleSmall, color = Wtt.OffWhite)
        }
        Box(Modifier.width(6.dp).height(32.dp).background(side?.let { Wtt.argb(s.team(it).info.colorArgb) } ?: Wtt.Black))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                EventText.describe(e, s), style = MaterialTheme.typography.bodyLarge,
                color = if (e.deleted) Wtt.Disabled else Wtt.OffWhite,
                textDecoration = if (e.deleted) TextDecoration.LineThrough else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.payload.kind, style = MaterialTheme.typography.labelSmall, color = Wtt.Muted)
                if (e.rev > 1) Pill("Edited", Wtt.PanelHigh, Wtt.Amber)
                if (e.deleted) Pill("Deleted", Wtt.Red, Wtt.OffWhite)
            }
        }
    }
}

/** Editable payload for score / foul / timeout events. */
private class PayloadDraft(p: EventPayload) {
    var kind by mutableStateOf(p.kind)
    var side by mutableStateOf(
        when (p) { is Score -> p.side; is Foul -> p.side; is TimeoutStarted -> p.side; is TimeoutsAdjusted -> p.side; else -> TeamSide.HOME }
    )
    var points by mutableStateOf((p as? Score)?.points ?: 2)
    var foulType by mutableStateOf((p as? Foul)?.type ?: FoulType.PERSONAL)
    var playerId by mutableStateOf((p as? Score)?.playerId ?: (p as? Foul)?.playerId)
    private val original = p
    val editable: Boolean = p is Score || p is Foul || p is TimeoutStarted

    fun build(s: GameState): EventPayload = if (!editable) original else when (kind) {
        "SCORE" -> Score(side, points, playerId)
        "FOUL" -> Foul(side, playerId, foulType)
        "TIMEOUT" -> if (original is TimeoutStarted) original.copy(side = side) else TimeoutStarted(side, s.rules.timeoutLengthMs)
        else -> original
    }
}

@Composable
private fun PayloadEditor(s: GameState, d: PayloadDraft) {
    if (!d.editable) {
        Text("Only the period and clock time of this event can be changed. Use Delete to remove it.",
            style = MaterialTheme.typography.bodyMedium, color = Wtt.Muted, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    SectionLabel("Team")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (side in TeamSide.entries) WttChip(s.team(side).info.shortName, d.side == side, {
            if (d.side != side) { d.side = side; d.playerId = null }
        }, Modifier.weight(1f))
    }
    when (d.kind) {
        "SCORE" -> {
            SectionLabel("Points")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (pts in 1..3) WttChip("+$pts", d.points == pts, { d.points = pts }, Modifier.weight(1f))
            }
        }
        "FOUL" -> {
            SectionLabel("Foul type")
            FoulTypeChips(d.foulType) { d.foulType = it }
        }
    }
    if (d.kind != "TIMEOUT") {
        SectionLabel("Player")
        JerseyGrid(s, d.side, selectedId = d.playerId, onPick = { d.playerId = it.id }, showFouls = d.kind == "FOUL")
        Spacer(Modifier.height(6.dp))
        WttChip("Team / no player", d.playerId == null, { d.playerId = null }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun WhenEditor(s: GameState, period: Int, onPeriod: (Int) -> Unit, clockText: String, onClock: (String) -> Unit) {
    SectionLabel("When")
    val maxP = maxOf(s.period, s.rules.regulationPeriods) + 1
    Stepper("Period", ClockFormat.periodShort(period, s.rules),
        { onPeriod((period - 1).coerceAtLeast(1)) }, { onPeriod((period + 1).coerceAtMost(maxP)) })
    WttTextField(clockText, { onClock(it.filter { c -> c.isDigit() || c == ':' || c == '.' }.take(8)) }, "Game clock (m:ss.t)", Modifier.fillMaxWidth())
}

@Composable
fun EventEditDialog(
    s: GameState,
    e: GameEvent,
    onDismiss: () -> Unit,
    onSave: (EventPayload, Int, Long) -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    val draft = remember(e.id, e.rev) { PayloadDraft(e.payload) }
    var period by remember(e.id, e.rev) { mutableStateOf(e.period) }
    var clockText by remember(e.id, e.rev) { mutableStateOf(ClockFormat.plain(e.gameClockMs, true)) }
    val clockMs = parseClock(clockText)
    val setup = e.payload is GameCreated
    WttDialog(EventText.describe(e, s), onDismiss, buttons = {
        when {
            setup -> Unit
            e.deleted -> WttButton("Restore", onRestore, kind = ButtonKind.SECONDARY, icon = WttIcons.Restore)
            else -> WttButton("Delete", onDelete, kind = ButtonKind.GHOST, icon = WttIcons.Trash)
        }
        if (!setup) WttButton("Save", { clockMs?.let { onSave(draft.build(s), period, it) } }, kind = ButtonKind.PRIMARY, enabled = clockMs != null)
    }) {
        Text("${EventText.whenText(e, s)} · revision ${e.rev} · by ${e.origin}", style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
        if (setup) {
            Text("Game setup is edited from Teams, rosters & rules.", style = MaterialTheme.typography.bodyMedium, color = Wtt.Muted, modifier = Modifier.padding(vertical = 8.dp))
            return@WttDialog
        }
        PayloadEditor(s, draft)
        WhenEditor(s, period, { period = it }, clockText, { clockText = it })
        Text("Totals, fouls, bonus and foul-outs are recomputed from the edited log.",
            style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun InsertEventDialog(s: GameState, onDismiss: () -> Unit, onInsert: (EventPayload, Int, Long) -> Unit) {
    val draft = remember { PayloadDraft(Score(TeamSide.HOME, 2)) }
    var period by remember { mutableStateOf(s.period) }
    var clockText by remember { mutableStateOf(ClockFormat.plain(s.clock.baseMs, true)) }
    val clockMs = parseClock(clockText)
    WttDialog("Add missed event", onDismiss, buttons = {
        WttButton("Cancel", onDismiss, kind = ButtonKind.GHOST)
        WttButton("Add", { clockMs?.let { onInsert(draft.build(s), period, it) } }, kind = ButtonKind.PRIMARY, enabled = clockMs != null)
    }) {
        SectionLabel("Kind")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in listOf("SCORE", "FOUL", "TIMEOUT")) WttChip(k, draft.kind == k, { draft.kind = k }, Modifier.weight(1f))
        }
        PayloadEditor(s, draft)
        WhenEditor(s, period, { period = it }, clockText, { clockText = it })
        Text("The event is added to the end of the log with the period and clock you enter.",
            style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}
