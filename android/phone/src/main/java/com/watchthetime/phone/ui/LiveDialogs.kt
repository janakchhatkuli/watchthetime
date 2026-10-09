package com.watchthetime.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.SegmentText
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttChip
import com.watchthetime.brand.WttIcons
import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.event.Stamp
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState

@Composable
fun FoulTypeChips(type: FoulType, onPick: (FoulType) -> Unit, types: List<FoulType> = FoulType.entries) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in types) {
            WttChip(
                t.code, type == t, { onPick(t) }, Modifier.weight(1f),
                selectedColor = if (t.isFlag) Wtt.Red else Wtt.Amber,
            )
        }
    }
}

/**
 * Foul entry: choose the type (Personal by default), then tap the jersey. With
 * "confirm foul type" on, a jersey tap selects and RECORD commits.
 */
@Composable
fun FoulDialog(
    s: GameState,
    side: TeamSide,
    confirmStep: Boolean,
    onDismiss: () -> Unit,
    onRecord: (playerId: String?, type: FoulType) -> Unit,
) {
    val foulTypes = s.rules.foulTypes
    var type by remember(foulTypes) { mutableStateOf(foulTypes.firstOrNull() ?: FoulType.PERSONAL) }
    var selected by remember { mutableStateOf<String?>(null) }
    var bench by remember { mutableStateOf(false) }
    WttDialog(
        "Foul · ${s.team(side).info.shortName}", onDismiss,
        buttons = {
            if (confirmStep) {
                WttButton("Cancel", onDismiss, kind = ButtonKind.GHOST)
                WttButton("Record foul", { onRecord(selected, type) }, kind = ButtonKind.PRIMARY, enabled = selected != null || bench)
            }
        },
    ) {
        SectionLabel("Type")
        FoulTypeChips(type, { type = it }, foulTypes)
        SectionLabel("Player")
        JerseyGrid(s, side, selectedId = selected, onPick = { p ->
            if (confirmStep) { selected = p.id; bench = false } else onRecord(p.id, type)
        })
        Spacer(Modifier.height(10.dp))
        WttButton(
            "Team / bench (no player)", {
                if (confirmStep) { selected = null; bench = true } else onRecord(null, type)
            },
            kind = if (bench) ButtonKind.PRIMARY else ButtonKind.GHOST, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Team fouls ${s.teamFouls(side)} · bonus from ${s.bonusThreshold()}",
            style = MaterialTheme.typography.bodySmall, color = Wtt.Muted,
        )
    }
}

/** Pick a player of [side] (or "team") — used to assign / pre-select a scorer. */
@Composable
fun PlayerPickDialog(s: GameState, side: TeamSide, title: String, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    WttDialog(title, onDismiss) {
        Spacer(Modifier.height(8.dp))
        JerseyGrid(s, side, onPick = { onPick(it.id) }, showFouls = false)
        Spacer(Modifier.height(10.dp))
        WttButton("Team (no player)", { onPick(null) }, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
    }
}

/** Log a set of free throws (made/attempts) for a team, optionally assigned to a shooter. */
@Composable
fun FreeThrowsDialog(
    s: GameState,
    side: TeamSide,
    confirmStep: Boolean,
    onDismiss: () -> Unit,
    onRecord: (playerId: String?, made: Int, attempts: Int) -> Unit,
) {
    var made by remember { mutableStateOf(1) }
    var attempts by remember { mutableStateOf(1) }
    var selected by remember { mutableStateOf<String?>(null) }
    var bench by remember { mutableStateOf(false) }
    WttDialog("Free throws · ${s.team(side).info.shortName}", onDismiss, buttons = {
        if (confirmStep) {
            WttButton("Cancel", onDismiss, kind = ButtonKind.GHOST)
            WttButton("Record", { onRecord(selected, made, attempts) }, kind = ButtonKind.PRIMARY)
        }
    }) {
        SectionLabel("Shooter")
        JerseyGrid(s, side, selectedId = selected, onPick = { p ->
            if (confirmStep) { selected = p.id; bench = false } else onRecord(p.id, made, attempts)
        })
        Spacer(Modifier.height(6.dp))
        WttButton("Team (no shooter)", {
            if (confirmStep) { selected = null; bench = true } else onRecord(null, made, attempts)
        }, kind = if (bench) ButtonKind.PRIMARY else ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
        SectionLabel("Attempts")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in 1..5) WttChip("$n", attempts == n, { attempts = n; if (made > n) made = n }, Modifier.weight(1f))
        }
        SectionLabel("Made")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in 0..5) WttChip("$n", made == n && n <= attempts, { if (n <= attempts) made = n }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text("Records one free-throw set; totals go to the shooter when one is picked.",
            style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
        Spacer(Modifier.height(6.dp))
    }
}

/** Simple substitution: pick the player leaving, then the player entering. */
@Composable
fun SubDialog(s: GameState, side: TeamSide, onDismiss: () -> Unit, onRecord: (outId: String, inId: String) -> Unit) {
    var out by remember { mutableStateOf<String?>(null) }
    val short = s.team(side).info.shortName
    WttDialog("Substitution · $short", onDismiss) {
        SectionLabel(if (out == null) "Player OUT" else "Player IN")
        JerseyGrid(
            s, side, selectedId = if (out == null) null else null, showFouls = false,
            onPick = { p ->
                if (out == null) {
                    if (p.disqualified(s.rules)) return@JerseyGrid // can't return an ejected player
                    out = p.id
                } else if (p.id != out) {
                    onRecord(out!!, p.id)
                    onDismiss()
                }
            },
        )
        if (out != null) {
            Text("Leaving: #${s.player(out)?.info?.number} ${s.player(out)?.info?.name} · tap the player entering",
                style = MaterialTheme.typography.bodySmall, color = Wtt.Amber, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Text("Tap the player leaving the court, then the one entering.",
                style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

/** Per-player quick actions: score, fouls, edit, remove. */
@Composable
fun PlayerDialog(s: GameState, playerId: String, send: (Command) -> Unit, onDismiss: () -> Unit) {
    val p = s.player(playerId) ?: run {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf(p.info.number) }
    var name by remember { mutableStateOf(p.info.name) }
    val side = p.info.side
    val enabled = !s.finalized
    WttDialog("#${p.info.number} ${p.info.name}".trim(), onDismiss, buttons = {
        if (editing) {
            WttButton("Cancel", { editing = false }, kind = ButtonKind.GHOST)
            WttButton("Save", { send(Command.UpsertPlayer(p.info.copy(number = number, name = name))); editing = false },
                kind = ButtonKind.PRIMARY, enabled = number.isNotBlank())
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamTag(s.team(side).info.shortName, s.team(side).info.colorArgb)
            Spacer(Modifier.width(10.dp))
            Text(
                "${p.points} PTS · ${p.countedFouls(s.rules)} PF" +
                    p.flagBadges().joinToString("") { " · ${it.second}${it.first.code}" } +
                    if (p.disqualified(s.rules)) " · OUT" else "",
                style = MaterialTheme.typography.titleMedium, color = if (p.disqualified(s.rules)) Wtt.Red else Wtt.OffWhite,
            )
        }
        if (editing) {
            Spacer(Modifier.height(12.dp))
            Row {
                WttTextField(number, { number = it.filter { c -> c.isDigit() }.take(2) }, "#", Modifier.width(80.dp), number = true)
                Spacer(Modifier.width(8.dp))
                WttTextField(name, { name = it.take(40) }, "Name", Modifier.weight(1f))
            }
            return@WttDialog
        }
        SectionLabel("Score")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (pts in s.rules.scoringPoints) WttButton("+$pts", { send(Command.AddScore(side, pts, p.id)); onDismiss() }, Modifier.weight(1f),
                enabled = enabled, minHeight = 56.dp, textStyle = MaterialTheme.typography.headlineSmall)
        }
        SectionLabel("Foul")
        val foulTypes = s.rules.foulTypes
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in foulTypes) WttButton(
                t.code, { send(Command.AddFoul(side, p.id, t)); onDismiss() }, Modifier.weight(1f), enabled = enabled,
                kind = if (t.isFlag) ButtonKind.DANGER else ButtonKind.SECONDARY, minHeight = 52.dp,
            )
        }
        SectionLabel("Roster")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WttButton("Edit", { editing = true }, Modifier.weight(1f), icon = WttIcons.Pencil, kind = ButtonKind.GHOST)
            WttButton("Remove", { send(Command.RemovePlayer(p.id)); onDismiss() }, Modifier.weight(1f), icon = WttIcons.Trash, kind = ButtonKind.GHOST)
        }
    }
}

/** Change the clock mode mid-game. Applying writes a logged, undoable event. */
@Composable
fun ClockModeDialog(s: GameState, send: (Command) -> Unit, onDismiss: () -> Unit) {
    var policy by remember { mutableStateOf(s.clockPolicy) }
    WttDialog("Clock mode", onDismiss, buttons = {
        WttButton("Cancel", onDismiss, kind = ButtonKind.GHOST)
        WttButton("Apply", { send(Command.SetClockPolicy(policy)); onDismiss() }, kind = ButtonKind.PRIMARY,
            enabled = policy != s.clockPolicy)
    }) {
        Text("Now: ${s.clockPolicy.summary}", style = MaterialTheme.typography.titleMedium, color = Wtt.Amber,
            modifier = Modifier.padding(top = 8.dp))
        ClockPolicyEditor(policy, s.rules) { policy = it }
        Text("The change is recorded in the event log and can be undone. The clock itself is not started or stopped.",
            style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(vertical = 8.dp))
    }
}

/** Game clock fine-tuning plus period controls. */
@Composable
fun ClockDialog(s: GameState, now: Stamp, send: (Command) -> Unit, onDismiss: () -> Unit) {
    val rem = s.clock.at(now)
    var setText by remember { mutableStateOf("") }
    WttDialog("Game clock", onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SegmentText(ClockFormat.game(rem, true, s.rules.lastMinuteWarningMs), 56.sp,
                color = if (s.clock.running) Wtt.Amber else Wtt.OffWhite)
            Text(ClockFormat.periodLong(s.period, s.rules) + if (s.clock.running) " · RUNNING" else " · STOPPED",
                style = MaterialTheme.typography.labelMedium, color = Wtt.Muted)
        }
        SectionLabel("Adjust")
        val steps = listOf(-10_000L to "−10s", -1_000L to "−1s", -100L to "−.1", 100L to "+.1", 1_000L to "+1s", 10_000L to "+10s")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((d, label) in steps) WttButton(label, { send(Command.AdjustClock(d)) }, Modifier.weight(1f), minHeight = 52.dp, enabled = !s.finalized)
        }
        SectionLabel("Set exactly")
        Row(verticalAlignment = Alignment.CenterVertically) {
            WttTextField(setText, { setText = it.filter { c -> c.isDigit() || c == ':' || c == '.' }.take(8) }, "m:ss.t", Modifier.weight(1f), number = false)
            Spacer(Modifier.width(8.dp))
            val ms = parseClock(setText)
            WttButton("Set", { ms?.let { send(Command.SetClock(it)) } }, kind = ButtonKind.PRIMARY,
                enabled = ms != null && ms <= s.rules.lengthOf(s.period) && !s.finalized, minHeight = 56.dp)
        }
        SectionLabel("Period")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WttButton("Previous", { send(Command.PreviousPeriod); onDismiss() }, Modifier.weight(1f), kind = ButtonKind.GHOST,
                enabled = s.finalized || s.period > 1)
            WttButton("End period", { send(Command.EndPeriod); onDismiss() }, Modifier.weight(1f), kind = ButtonKind.GHOST,
                enabled = !s.finalized && (s.clock.running || s.clock.baseMs > 0))
            WttButton("Next", { send(Command.NextPeriod); onDismiss() }, Modifier.weight(1f), kind = ButtonKind.GHOST, enabled = !s.finalized)
        }
        Spacer(Modifier.height(6.dp))
        if (s.finalized) WttButton("Reopen game", { send(Command.ReopenGame); onDismiss() }, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY)
        else WttButton("Final", { send(Command.FinalizeGame); onDismiss() }, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ShotDialog(s: GameState, now: Stamp, send: (Command) -> Unit, onDismiss: () -> Unit) {
    val v = s.shot.at(now)
    WttDialog("Shot clock", onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SegmentText(ClockFormat.shot(v), 64.sp, color = if (s.shotHeld) Wtt.Muted else Wtt.Red)
            if (s.shotHeld) Text("HOLD", style = MaterialTheme.typography.labelMedium, color = Wtt.Muted)
        }
        SectionLabel("Reset")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WttButton("${s.rules.shotClockFullMs / 1000}", { send(Command.ResetShotClock(false)) }, Modifier.weight(1f), kind = ButtonKind.PRIMARY, minHeight = 56.dp)
            WttButton("${s.rules.shotClockShortMs / 1000}", { send(Command.ResetShotClock(true)) }, Modifier.weight(1f), kind = ButtonKind.PRIMARY, minHeight = 56.dp)
        }
        SectionLabel("Adjust")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WttButton("−1s", { send(Command.AdjustShotClock(-1000)) }, Modifier.weight(1f), minHeight = 52.dp)
            WttButton("+1s", { send(Command.AdjustShotClock(1000)) }, Modifier.weight(1f), minHeight = 52.dp)
            WttButton(if (s.shotHeld) "Run" else "Hold", { send(Command.ToggleShotHold) }, Modifier.weight(1f), minHeight = 52.dp, kind = ButtonKind.GHOST)
        }
        Text("Hold freezes the shot clock while the game clock runs (e.g. under the shot-clock-off rule).",
            style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(top = 8.dp))
        HRule(Modifier.padding(top = 8.dp), color = Wtt.Panel)
    }
}
