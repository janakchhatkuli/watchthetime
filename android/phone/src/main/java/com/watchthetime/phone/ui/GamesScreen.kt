package com.watchthetime.phone.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.watchthetime.brand.SegmentText
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttIconButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.data.GameEntity
import com.watchthetime.domain.state.GameStatus
import com.watchthetime.phone.LiveGame
import com.watchthetime.phone.container
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(onNew: () -> Unit, onOpen: (String) -> Unit, onTeams: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val games by c.games.observeGames().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<GameEntity?>(null) }

    WttScreen(
        title = "Watch the Time", onBack = null, subtitle = "Basketball scoreboard",
        actions = {
            WttIconButton(WttIcons.Jersey, "Teams", onTeams)
            WttIconButton(WttIcons.Sliders, "Settings", onSettings)
        },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = games
            when {
                list == null -> Unit
                list.isEmpty() -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    EmptyNote("No games yet.\nStart one: pick a rules preset, two teams and go.")
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.gameId }) { g ->
                        GameRow(g, onClick = { onOpen(g.gameId) }, onLongClick = { confirmDelete = g })
                        HRule()
                    }
                }
            }
        }
        HRule()
        WttButton(
            "New game", onNew, kind = ButtonKind.PRIMARY, icon = WttIcons.Plus,
            modifier = Modifier.fillMaxWidth().padding(16.dp), minHeight = 64.dp,
            textStyle = MaterialTheme.typography.titleLarge,
        )
    }

    confirmDelete?.let { g ->
        ConfirmDialog(
            title = "Delete game?",
            message = "${g.homeName} ${g.homeScore} – ${g.awayScore} ${g.awayName}\nThe full event log is removed. This can't be undone.",
            confirm = "Delete",
            onConfirm = { scope.launch { c.controller.deleteGame(g.gameId) } },
            onDismiss = { confirmDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameRow(g: GameEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    val live = g.status == GameStatus.LIVE.name || g.status == GameStatus.PERIOD_BREAK.name
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Delete game")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamTag(g.homeShort, g.homeColor)
                Text("  vs  ", style = MaterialTheme.typography.labelMedium, color = Wtt.Muted)
                TeamTag(g.awayShort, g.awayColor)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                (if (g.title.isNotBlank()) g.title + " · " else "") +
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(g.createdWallMs)),
                style = MaterialTheme.typography.bodySmall, color = Wtt.Muted,
            )
            Spacer(Modifier.height(2.dp))
            Pill(
                if (g.status == GameStatus.FINAL.name) "FINAL" else g.progress,
                bg = if (live) Wtt.Amber else Wtt.PanelHigh, fg = if (live) Wtt.Black else Wtt.OffWhite,
            )
        }
        Spacer(Modifier.width(12.dp))
        val col = if (g.status == GameStatus.FINAL.name) Wtt.OffWhite else Wtt.Amber
        SegmentText(pad3(g.homeScore), 26.sp, color = col, spoken = "${g.homeName} ${g.homeScore}")
        Text(" – ", style = MaterialTheme.typography.titleLarge, color = Wtt.Muted)
        SegmentText(pad3(g.awayScore), 26.sp, color = col, spoken = "${g.awayName} ${g.awayScore}")
    }
}

internal fun pad3(n: Int): String = n.toString().padStart(3, '!').takeLast(3)

/** Opens [gameId] in the controller and returns its live snapshot (null while loading). */
@Composable
fun rememberLiveGame(gameId: String): LiveGame? {
    val c = LocalContext.current.container
    LaunchedEffect(gameId) { c.controller.open(gameId) }
    val live by c.controller.live.collectAsState()
    return live?.takeIf { it.gameId == gameId }
}
