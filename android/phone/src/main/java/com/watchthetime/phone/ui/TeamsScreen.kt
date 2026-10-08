package com.watchthetime.phone.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.EmptyNote
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttIconButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.data.RosterCsv
import com.watchthetime.data.SavedPlayer
import com.watchthetime.data.SavedTeam
import com.watchthetime.data.TeamRepository
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.phone.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun TeamsScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val c = LocalContext.current.container
    val teams by c.teams.observeTeams().collectAsState(initial = null)
    WttScreen("Teams", onBack, subtitle = "Saved teams and rosters") {
        Box(Modifier.weight(1f)) {
            val list = teams
            if (list != null && list.isEmpty()) EmptyNote("No saved teams.\nTeams are saved when you create a game, or add one here.")
            LazyColumn(Modifier.fillMaxSize()) {
                items(list.orEmpty(), key = { it.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onEdit(t.id) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamTag(t.info.shortName, t.info.colorArgb)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.info.name, style = MaterialTheme.typography.titleMedium, color = Wtt.OffWhite)
                            Text("${t.players.size} players", style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
                        }
                        Icon(WttIcons.ChevronRight, null, tint = Wtt.Muted)
                    }
                    HRule()
                }
            }
        }
        HRule()
        WttButton("New team", { onEdit("new") }, kind = ButtonKind.PRIMARY, icon = WttIcons.Plus,
            modifier = Modifier.fillMaxWidth().padding(16.dp), minHeight = 56.dp)
    }
}

/** Reads a roster CSV picked through the system file picker. */
suspend fun readCsv(ctx: android.content.Context, uri: Uri): RosterCsv.Result? = withContext(Dispatchers.IO) {
    runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()?.let { RosterCsv.parse(it) }
}

@Composable
fun TeamEditScreen(teamId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val isNew = teamId == "new"
    val id = remember { if (isNew) UUID.randomUUID().toString() else teamId }
    var info by remember { mutableStateOf(TeamInfo("", "", TeamInfo.default(TeamSide.HOME).colorArgb)) }
    var players by remember { mutableStateOf<List<SavedPlayer>>(emptyList()) }
    var loaded by remember { mutableStateOf(isNew) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(teamId) {
        if (!isNew) {
            c.teams.observeTeams().first().firstOrNull { it.id == teamId }?.let { info = it.info; players = it.players }
            loaded = true
        }
    }

    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = readCsv(ctx, uri)
            if (r == null) { note = "Couldn't read that file"; return@launch }
            val existing = players.associateBy { it.number }
            val merged = (existing + r.players.associateBy { it.number }).values.sortedWith(TeamRepository.RosterOrder)
            players = merged
            note = "Imported ${r.players.size} players" + if (r.skipped.isNotEmpty()) " · skipped ${r.skipped.size} lines" else ""
        }
    }

    val problem = when {
        info.name.isBlank() -> "Team needs a name"
        info.shortName.isBlank() -> "Team needs a short name"
        else -> rosterProblem(players)
    }

    WttScreen(if (isNew) "New team" else info.name.ifBlank { "Team" }, onBack, actions = {
        if (!isNew) WttIconButton(WttIcons.Trash, "Delete team", { confirmDelete = true })
    }) {
        if (!loaded) return@WttScreen
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            TeamInfoEditor(info) { info = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Roster · ${players.size}", Modifier.weight(1f))
                WttButton("Import CSV", { import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
                    kind = ButtonKind.GHOST, icon = WttIcons.Import, minHeight = 40.dp)
            }
            Text("CSV: one player per line, \"number,name\". A header line is ignored.", style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Wtt.Amber, modifier = Modifier.padding(vertical = 4.dp)) }
            RosterEditor(players) { players = it }
            Spacer(Modifier.height(16.dp))
        }
        HRule()
        problem?.let { Text(it, color = Wtt.Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WttButton("Save", {
                scope.launch { c.teams.save(SavedTeam(id, info, players)); onBack() }
            }, kind = ButtonKind.PRIMARY, enabled = problem == null, modifier = Modifier.fillMaxWidth(), minHeight = 56.dp)
        }
    }

    if (confirmDelete) ConfirmDialog(
        "Delete team?", "${info.name} and its roster are removed. Existing games are not affected.", "Delete",
        onConfirm = { scope.launch { c.teams.delete(id); onBack() } }, onDismiss = { confirmDelete = false },
    )
}
