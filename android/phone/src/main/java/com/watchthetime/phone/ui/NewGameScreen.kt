package com.watchthetime.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttChip
import com.watchthetime.data.SavedPlayer
import com.watchthetime.data.SavedTeam
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.Rules
import com.watchthetime.phone.container
import kotlinx.coroutines.launch
import java.util.UUID

private class SideDraft(side: TeamSide) {
    var savedId by mutableStateOf<String?>(null)
    var info by mutableStateOf(TeamInfo.default(side))
    var players by mutableStateOf<List<SavedPlayer>>(emptyList())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGameScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val c = LocalContext.current.container
    val settings by c.controller.settings.collectAsState()
    val savedTeams by c.teams.observeTeams().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var rules by remember { mutableStateOf(Rules.preset(settings.game.defaultPreset)) }
    var rulesTouched by remember { mutableStateOf(false) }
    var policy by remember { mutableStateOf(ClockPolicy.NEW_GAME) }
    var title by remember { mutableStateOf("") }
    var saveTeams by remember { mutableStateOf(true) }
    val home = remember { SideDraft(TeamSide.HOME) }
    val away = remember { SideDraft(TeamSide.AWAY) }

    // Settings load asynchronously; apply the default preset unless the user already chose.
    LaunchedEffect(settings.game.defaultPreset) {
        if (!rulesTouched) rules = Rules.preset(settings.game.defaultPreset)
    }

    val steps = listOf("Rules", "Teams", "Rosters")
    val problem = when (step) {
        1 -> when {
            home.info.name.isBlank() || away.info.name.isBlank() -> "Both teams need a name"
            home.info.shortName.isBlank() || away.info.shortName.isBlank() -> "Both teams need a short name"
            home.info.shortName == away.info.shortName -> "Short names must differ"
            else -> null
        }
        2 -> rosterProblem(home.players, rules)?.let { "HOME: $it" } ?: rosterProblem(away.players, rules)?.let { "AWAY: $it" }
        else -> null
    }

    fun create() {
        val roster = home.players.map { PlayerInfo(UUID.randomUUID().toString(), TeamSide.HOME, it.number.trim(), it.name.trim()) } +
            away.players.map { PlayerInfo(UUID.randomUUID().toString(), TeamSide.AWAY, it.number.trim(), it.name.trim()) }
        if (saveTeams) scope.launch {
            for (d in listOf(home, away)) {
                c.teams.save(SavedTeam(d.savedId ?: UUID.randomUUID().toString(), d.info, d.players))
            }
        }
        val id = c.controller.create(rules, home.info, away.info, roster, title.trim(), policy)
        onCreated(id)
    }

    WttScreen(title = "New game", subtitle = "Step ${step + 1} of 3 · ${steps[step]}", onBack = {
        if (step > 0) step-- else onBack()
    }) {
        Row(Modifier.fillMaxWidth()) {
            steps.forEachIndexed { i, _ ->
                Box(
                    Modifier.weight(1f).height(4.dp).padding(horizontal = 1.dp)
                        .background(if (i <= step) Wtt.Amber else Wtt.PanelHigh)
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(12.dp))
                    WttTextField(title, { title = it.take(60) }, "Title (optional)", Modifier.fillMaxWidth())
                    ClockPolicyEditor(policy, rules) { policy = it }
                    RulesEditor(rules, { rules = it; rulesTouched = true })
                }
                1 -> for ((side, d) in listOf(TeamSide.HOME to home, TeamSide.AWAY to away)) {
                    SectionLabel(if (side == TeamSide.HOME) "Home team" else "Away team", color = Wtt.OffWhite)
                    if (savedTeams.isNotEmpty()) {
                        Text("SAVED TEAMS", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (t in savedTeams) {
                                WttChip(t.info.shortName.ifBlank { t.info.name }, d.savedId == t.id, {
                                    if (d.savedId == t.id) {
                                        d.savedId = null; d.info = TeamInfo.default(side); d.players = emptyList()
                                    } else {
                                        d.savedId = t.id; d.info = t.info; d.players = t.players
                                    }
                                })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    TeamInfoEditor(d.info) { d.info = it }
                    Spacer(Modifier.height(8.dp))
                    HRule()
                }
                2 -> {
                    for ((side, d) in listOf(TeamSide.HOME to home, TeamSide.AWAY to away)) {
                        Row(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                            TeamTag(d.info.shortName, d.info.colorArgb)
                            Text("  ${d.info.name}  ·  ${d.players.size} players", style = MaterialTheme.typography.titleMedium, color = Wtt.OffWhite)
                        }
                        RosterEditor(d.players) { d.players = it }
                        if (side == TeamSide.HOME) HRule(Modifier.padding(top = 12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Rosters are optional: without them, points and fouls count for the team only. You can edit rosters during the game.",
                        style = MaterialTheme.typography.bodySmall, color = Wtt.Muted,
                    )
                    ToggleRow("Save teams for next time", saveTeams, { saveTeams = it })
                }
            }
        }
        HRule()
        problem?.let { Text(it, color = Wtt.Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 0) WttButton("Back", { step-- }, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f), minHeight = 56.dp)
            if (step < 2) {
                WttButton("Next", { step++ }, kind = ButtonKind.PRIMARY, enabled = problem == null, modifier = Modifier.weight(2f), minHeight = 56.dp)
            } else {
                WttButton("Create game", ::create, kind = ButtonKind.PRIMARY, enabled = problem == null, modifier = Modifier.weight(2f), minHeight = 56.dp)
            }
        }
    }
}
