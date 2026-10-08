package com.watchthetime.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.HRule
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttIconButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.domain.model.TeamColors
import com.watchthetime.domain.state.GameState
import com.watchthetime.domain.state.PlayerState
import com.watchthetime.domain.model.TeamSide

/** Screen frame: square top bar (back, title, actions) over black. */
@Composable
fun WttScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(Wtt.Black).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) WttIconButton(WttIcons.Back, "Back", onBack)
            else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Wtt.OffWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = Wtt.Muted, maxLines = 1)
            }
            actions()
        }
        HRule()
        content()
    }
}

@Composable
fun WttDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth()
                .background(Wtt.Panel).border(1.dp, Wtt.Line),
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Wtt.Amber))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Wtt.OffWhite, modifier = Modifier.weight(1f))
                WttIconButton(WttIcons.Close, "Close", onDismiss)
            }
            Column(
                Modifier.padding(horizontal = 16.dp).weight(1f, fill = false)
                    .let { if (scroll) it.verticalScroll(rememberScrollState()) else it },
            ) { content() }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                content = buttons,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    WttDialog(title, onDismiss, buttons = {
        WttButton("Cancel", onDismiss, kind = ButtonKind.GHOST)
        WttButton(confirm, { onConfirm(); onDismiss() }, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY)
    }) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Wtt.OffWhite)
    }
}

@Composable
fun WttTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    caps: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Wtt.Amber,
            unfocusedBorderColor = Wtt.Line,
            focusedLabelColor = Wtt.Amber,
            unfocusedLabelColor = Wtt.Muted,
            cursorColor = Wtt.Amber,
            focusedTextColor = Wtt.OffWhite,
            unfocusedTextColor = Wtt.OffWhite,
        ),
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Wtt.OffWhite)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Wtt.Black, checkedTrackColor = Wtt.Amber, checkedBorderColor = Wtt.Amber,
                uncheckedThumbColor = Wtt.Muted, uncheckedTrackColor = Wtt.Black, uncheckedBorderColor = Wtt.Line,
            ),
        )
    }
}

/** Fixed jersey palette picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Long, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in TeamColors.entries) {
            val sel = c.argb == selected
            Box(
                Modifier.size(44.dp)
                    .border(if (sel) 3.dp else 1.dp, if (sel) Wtt.Amber else Wtt.Line)
                    .padding(if (sel) 5.dp else 3.dp)
                    .background(Wtt.argb(c.argb))
                    .clickable(role = Role.RadioButton, onClickLabel = c.label) { onPick(c.argb) },
            )
        }
    }
}

/**
 * Jersey number grid for one side. Shows each player's counted fouls; fouled-out players are
 * marked red but stay selectable (corrections must remain possible).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JerseyGrid(
    state: GameState,
    side: TeamSide,
    onPick: (PlayerState) -> Unit,
    selectedId: String? = null,
    showFouls: Boolean = true,
) {
    val roster = state.roster(side)
    if (roster.isEmpty()) {
        Text("No players on the roster.", style = MaterialTheme.typography.bodyMedium, color = Wtt.Muted, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in roster) {
            val out = p.disqualified(state.rules)
            val trouble = p.inFoulTrouble(state.rules)
            val sel = p.id == selectedId
            val bg = when { sel -> Wtt.Amber; out -> Wtt.Red; else -> Wtt.PanelHigh }
            val fg = when { sel -> Wtt.Black; else -> Wtt.OffWhite }
            Column(
                Modifier.size(width = 64.dp, height = 60.dp).background(bg)
                    .border(1.dp, if (trouble && !sel) Wtt.Amber else Wtt.Line)
                    .clickable(role = Role.Button, onClickLabel = "Player ${p.info.label}") { onPick(p) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(p.info.number, style = MaterialTheme.typography.headlineSmall, color = fg, fontWeight = FontWeight.ExtraBold)
                if (showFouls) {
                    val badges = p.flagBadges().joinToString("") { it.first.code.repeat(it.second) }
                    Text(
                        "${p.countedFouls(state.rules)} PF $badges".trim(),
                        style = MaterialTheme.typography.labelSmall, color = if (sel) Wtt.Black else Wtt.Muted,
                        maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Team colour block with short name (team colour is a block, never a text colour). */
@Composable
fun TeamTag(name: String, argb: Long, modifier: Modifier = Modifier) {
    Box(modifier.background(Wtt.argb(argb)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(name.uppercase(), style = MaterialTheme.typography.labelLarge, color = Wtt.onTeam(argb), maxLines = 1)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** "m:ss.t" / "m:ss" / "ss.t" → milliseconds, or null. */
fun parseClock(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return runCatching {
        val (minPart, secPart) = if (':' in t) t.substringBefore(':') to t.substringAfter(':') else "0" to t
        val min = minPart.toLong()
        val sec = secPart.toDouble()
        if (min < 0 || sec < 0 || sec >= 60) null else min * 60_000 + (sec * 1000).toLong()
    }.getOrNull()
}
