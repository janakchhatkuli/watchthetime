package com.watchthetime.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.Stepper
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttChip
import com.watchthetime.brand.WttIconButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState
import com.watchthetime.phone.container
import java.util.UUID

/** Mid-game editing of teams, rosters, rules and timeout counts (all event-sourced). */
@Composable
fun SetupScreen(gameId: String, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val live = rememberLiveGame(gameId)
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    if (live == null) { WttScreen("Teams & rules", onBack) {}; return }
    val s = live.state

    fun run(cmd: Command, ok: String) {
        val out = c.controller.dispatch(cmd)
        message = out.error ?: ok
    }

    val tabs = listOf(s.team(TeamSide.HOME).info.shortName, s.team(TeamSide.AWAY).info.shortName, "Rules & clock", "Timeouts")
    WttScreen("Teams & rules", onBack, subtitle = "Changes are logged and recomputed") {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, t -> WttChip(t, tab == i, { tab = i; message = null }, Modifier.weight(1f)) }
        }
        HRule()
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it.endsWith("saved", true) || it.endsWith("added", true)) Wtt.Amber else Wtt.Red,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (tab) {
                0, 1 -> TeamTab(s, if (tab == 0) TeamSide.HOME else TeamSide.AWAY, live.version, ::run)
                2 -> RulesTab(s, live.version, ::run)
                3 -> for (side in TeamSide.entries) {
                    Stepper(
                        "${s.team(side).info.name} timeouts left", "${s.timeoutsRemaining(side)}",
                        { run(Command.AdjustTimeouts(side, -1), "Timeouts saved") },
                        { run(Command.AdjustTimeouts(side, 1), "Timeouts saved") },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TeamTab(s: GameState, side: TeamSide, version: Long, run: (Command, String) -> Unit) {
    var info by remember(side) { mutableStateOf(s.team(side).info) }
    var newNumber by remember(side) { mutableStateOf("") }
    var newName by remember(side) { mutableStateOf("") }
    var editing by remember(side) { mutableStateOf<PlayerInfo?>(null) }

    SectionLabel("Team")
    TeamInfoEditor(info) { info = it }
    WttButton("Save team", { run(Command.UpdateTeam(side, info), "Team saved") }, Modifier.fillMaxWidth().padding(top = 8.dp),
        kind = ButtonKind.PRIMARY, enabled = info != s.team(side).info && info.name.isNotBlank() && info.shortName.isNotBlank())

    SectionLabel("Roster · ${s.roster(side).size}")
    for (p in s.roster(side)) {
        val e = editing
        if (e != null && e.id == p.id) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WttTextField(e.number, { editing = e.copy(number = it.filter { c -> c.isDigit() }.take(2)) }, "#", Modifier.width(76.dp), number = true)
                Spacer(Modifier.width(8.dp))
                WttTextField(e.name, { editing = e.copy(name = it.take(40)) }, "Name", Modifier.weight(1f))
                WttIconButton(WttIcons.Check, "Save player", { run(Command.UpsertPlayer(e), "Player saved"); editing = null }, tint = Wtt.Amber)
            }
        } else {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.info.number, style = MaterialTheme.typography.titleMedium, color = Wtt.OffWhite, modifier = Modifier.width(40.dp))
                Text(p.info.name.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = Wtt.OffWhite, modifier = Modifier.weight(1f))
                Text("${p.points} PTS · ${p.countedFouls(s.rules)} PF", style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
                WttIconButton(WttIcons.Pencil, "Edit #${p.info.number}", { editing = p.info }, tint = Wtt.Muted)
                WttIconButton(WttIcons.Trash, "Remove #${p.info.number}", { run(Command.RemovePlayer(p.id), "Player saved") }, tint = Wtt.Muted)
            }
        }
        HRule()
    }
    SectionLabel("Add player")
    Row(verticalAlignment = Alignment.CenterVertically) {
        WttTextField(newNumber, { newNumber = it.filter { c -> c.isDigit() }.take(2) }, "#", Modifier.width(76.dp), number = true)
        Spacer(Modifier.width(8.dp))
        WttTextField(newName, { newName = it.take(40) }, "Name", Modifier.weight(1f))
        WttIconButton(WttIcons.Plus, "Add player", {
            run(Command.UpsertPlayer(PlayerInfo(UUID.randomUUID().toString(), side, newNumber, newName)), "Player added")
            if (s.playerByNumber(side, newNumber) == null) { newNumber = ""; newName = "" }
        }, enabled = newNumber.isNotBlank(), tint = Wtt.Amber)
    }
}

@Composable
private fun RulesTab(s: GameState, version: Long, run: (Command, String) -> Unit) {
    var policy by remember(s.clockPolicy) { mutableStateOf(s.clockPolicy) }
    ClockPolicyEditor(policy, s.rules) { policy = it }
    WttButton("Save clock mode", { run(Command.SetClockPolicy(policy), "Clock mode saved") }, Modifier.fillMaxWidth().padding(top = 8.dp),
        kind = ButtonKind.PRIMARY, enabled = policy != s.clockPolicy)
    Text("Now: ${s.clockPolicy.summary}. The change is logged and can be undone.",
        style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(top = 6.dp))
    HRule(Modifier.padding(top = 12.dp))

    var rules by remember { mutableStateOf(s.rules) }
    WttButton("Save rules", { run(Command.UpdateRules(rules), "Rules saved") }, Modifier.fillMaxWidth().padding(top = 12.dp),
        kind = ButtonKind.PRIMARY, enabled = rules != s.rules)
    Text("Changing rules mid-game recomputes bonus, foul-outs and timeouts from the log.",
        style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(top = 6.dp))
    RulesEditor(rules, { rules = it }, lockedFormat = s.everStarted)
}
