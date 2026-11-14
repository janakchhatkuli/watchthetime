package com.watchthetime.brand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Seven-segment readout: the "all segments" ghost is drawn dimmed behind the live digits, like
 * a real LED scoreboard. Pass DSEG-formatted text (see ClockFormat; '!' is a digit-width blank).
 */
@Composable
fun SegmentText(
    text: String,
    size: TextUnit,
    color: Color = Wtt.Amber,
    modifier: Modifier = Modifier,
    ghost: Boolean = true,
    spoken: String = text.replace("!", ""),
) {
    val style = TextStyle(fontFamily = WttFonts.Segment, fontSize = size, fontWeight = FontWeight.Bold, color = color)
    Box(modifier.semantics { contentDescription = spoken }) {
        if (ghost) {
            val g = text.map { if (it.isDigit() || it == '!') '8' else it }.joinToString("")
            Text(g, style = style.copy(color = Wtt.Ghost), maxLines = 1, softWrap = false)
        }
        Text(text, style = style, maxLines = 1, softWrap = false)
    }
}

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, GHOST }

/**
 * Flat, rounded scoreboard button. [onLongClick] is optional (used for edit shortcuts).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WttButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    container: Color? = null,
    content: Color? = null,
    minHeight: Dp = 48.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    onLongClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    shape: Shape = WttShape.Medium,
) {
    val (bg, fg, border) = when (kind) {
        ButtonKind.PRIMARY -> Triple(Wtt.Amber, Wtt.Black, null)
        ButtonKind.SECONDARY -> Triple(Wtt.PanelHigh, Wtt.OffWhite, null)
        ButtonKind.DANGER -> Triple(Wtt.Red, Wtt.OffWhite, null)
        ButtonKind.GHOST -> Triple(Color.Transparent, Wtt.OffWhite, BorderStroke(1.dp, Wtt.Line))
    }
    val cBg = if (!enabled) Wtt.Panel else container ?: bg
    val cFg = if (!enabled) Wtt.Disabled else content ?: fg
    Row(
        modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(cBg)
            .let { if (border != null) it.border(border, shape) else it }
            .combinedClickable(
                enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = cFg, modifier = Modifier.size(20.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) {
            Text(
                text.uppercase(), style = textStyle, color = cFg, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, contentDescription = null, tint = cFg, modifier = Modifier.size(20.dp))
        }
    }
}

/** Round icon-only button with an accessible label. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WttIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Wtt.OffWhite,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(WttShape.Pill)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else Wtt.Disabled, modifier = Modifier.size(24.dp))
    }
}

/** Small uppercase section heading led by an amber icon (or a tick when there is none). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Wtt.Muted, icon: ImageVector? = null) {
    Row(modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Wtt.Amber, modifier = Modifier.size(18.dp))
        else Box(Modifier.size(width = 4.dp, height = 14.dp).clip(WttShape.Pill).background(Wtt.Amber))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun HRule(modifier: Modifier = Modifier, color: Color = Wtt.Line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Solid team colour swatch (team colours are never used as text colour). */
@Composable
fun TeamSwatch(argb: Long, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Box(modifier.size(size).clip(WttShape.Pill).background(Wtt.argb(argb)).border(1.dp, Wtt.Line, WttShape.Pill))
}

/** Pill-shaped selectable chip (for presets, foul types, toggles). */
@Composable
fun WttChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Wtt.Amber,
    icon: ImageVector? = null,
) {
    WttButton(
        text = text, onClick = onClick, modifier = modifier, icon = icon, shape = WttShape.Pill,
        kind = if (selected) ButtonKind.PRIMARY else ButtonKind.GHOST,
        container = if (selected) selectedColor else null,
        content = if (selected) (if (selectedColor == Wtt.Red) Wtt.OffWhite else Wtt.Black) else null,
        minHeight = 40.dp,
    )
}

/** Labelled − value + stepper. */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Wtt.OffWhite, modifier = Modifier.weight(1f))
        WttIconButton(WttIcons.Minus, "Decrease $label", onMinus, Modifier.clip(WttShape.Pill).background(Wtt.PanelHigh))
        Text(
            value, style = MaterialTheme.typography.titleLarge, color = Wtt.Amber,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp),
        )
        WttIconButton(WttIcons.Plus, "Increase $label", onPlus, Modifier.clip(WttShape.Pill).background(Wtt.PanelHigh))
    }
}

/** Flat panel with optional title. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(WttShape.Medium).background(Wtt.Panel).border(1.dp, Wtt.Line, WttShape.Medium)) { content() }
}

@Composable
fun RowScope.Cell(text: String, weight: Float, color: Color = Wtt.OffWhite, align: TextAlign = TextAlign.Center, bold: Boolean = false) {
    Text(
        text, modifier = Modifier.weight(weight), color = color, textAlign = align, maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Wtt.Disabled, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Wtt.Muted, textAlign = TextAlign.Center, fontSize = 18.sp)
    }
}
