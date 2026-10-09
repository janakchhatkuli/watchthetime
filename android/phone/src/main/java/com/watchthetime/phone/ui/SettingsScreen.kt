package com.watchthetime.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttChip
import com.watchthetime.brand.WttIconButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.rules.RulePreset
import com.watchthetime.domain.settings.AppSettings
import com.watchthetime.domain.settings.FeedbackMode
import com.watchthetime.feedback.CueCatalog
import com.watchthetime.phone.container
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val st by c.controller.settings.collectAsState()
    val scope = rememberCoroutineScope()
    fun update(f: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(f) } }
    val fb = st.feedback

    WttScreen("Settings", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("Sound & haptics")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in FeedbackMode.entries) WttChip(m.label, fb.mode == m, { update { it.copy(feedback = it.feedback.copy(mode = m)) } })
            }
            Spacer(Modifier.height(8.dp))
            Text("Audio output: ${c.controller.outputRoute().label}", style = MaterialTheme.typography.bodyMedium, color = Wtt.Muted)
            Text("With earbuds connected, cues play in the earbuds only.", style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
            LabeledSlider("Volume", fb.masterVolume) { v -> update { it.copy(feedback = it.feedback.copy(masterVolume = v)) } }
            LabeledSlider("Vibration strength", fb.hapticStrength) { v -> update { it.copy(feedback = it.feedback.copy(hapticStrength = v.coerceAtLeast(0.1f))) } }

            SectionLabel("Cues")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MOMENT", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted, modifier = Modifier.weight(1f))
                Text("SOUND", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted, modifier = Modifier.padding(end = 4.dp))
                Text("VIBR.", style = MaterialTheme.typography.labelSmall, color = Wtt.Muted, modifier = Modifier.padding(end = 52.dp))
            }
            for (type in CueType.entries) {
                CueRow(
                    type,
                    sound = type !in fb.soundOff, haptic = type !in fb.hapticOff,
                    onSound = { on -> update { it.copy(feedback = it.feedback.copy(soundOff = if (on) it.feedback.soundOff - type else it.feedback.soundOff + type)) } },
                    onHaptic = { on -> update { it.copy(feedback = it.feedback.copy(hapticOff = if (on) it.feedback.hapticOff - type else it.feedback.hapticOff + type)) } },
                    onPreview = { c.controller.previewCue(type) },
                )
                HRule()
            }

            SectionLabel("Display")
            ToggleRow("Tenths of a second in the last minute", st.display.showTenthsInLastMinute,
                { v -> update { it.copy(display = it.display.copy(showTenthsInLastMinute = v)) } })
            ToggleRow("Blink the clock when stopped", st.display.blinkWhenStopped,
                { v -> update { it.copy(display = it.display.copy(blinkWhenStopped = v)) } },
                "A stopped clock is always red; this also makes it pulse slowly")
            ToggleRow("Offer “Assign #” after a score", st.display.promptScorer,
                { v -> update { it.copy(display = it.display.copy(promptScorer = v)) } }, "Attribute team points to a player afterwards")
            ToggleRow("Confirm fouls with a button", st.display.confirmFoulType,
                { v -> update { it.copy(display = it.display.copy(confirmFoulType = v)) } }, "Off: tapping a jersey records the foul immediately")

            SectionLabel("Game")
            Text("Default rules for new games", style = MaterialTheme.typography.bodyLarge, color = Wtt.OffWhite)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in RulePreset.entries.filter { it != RulePreset.CUSTOM }) {
                    WttChip(p.label, st.game.defaultPreset == p, { update { it.copy(game = it.game.copy(defaultPreset = p)) } })
                }
            }
            ToggleRow("Shot-clock violation stops the game clock", st.game.shotExpiryStopsGame,
                { v -> update { it.copy(game = it.game.copy(shotExpiryStopsGame = v)) } })
            ToggleRow("Safe mode", st.gestures.safeMode,
                { v -> update { it.copy(gestures = it.gestures.copy(safeMode = v)) } },
                "Ask before undo, end period, final, set clock, delete")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Wtt.OffWhite, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = Wtt.Amber)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Wtt.Amber, activeTrackColor = Wtt.Amber, inactiveTrackColor = Wtt.PanelHigh),
        )
    }
}

@Composable
private fun CueRow(
    type: CueType,
    sound: Boolean,
    haptic: Boolean,
    onSound: (Boolean) -> Unit,
    onHaptic: (Boolean) -> Unit,
    onPreview: () -> Unit,
) {
    val spec = CueCatalog.of(type)
    val colors = CheckboxDefaults.colors(checkedColor = Wtt.Amber, uncheckedColor = Wtt.Muted, checkmarkColor = Wtt.Black)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(type.label, style = MaterialTheme.typography.bodyLarge, color = Wtt.OffWhite)
            Text("${spec.soundText} · ${spec.hapticText}", style = MaterialTheme.typography.bodySmall, color = Wtt.Muted)
        }
        Checkbox(sound, onSound, colors = colors)
        Checkbox(haptic, onHaptic, colors = colors)
        WttIconButton(WttIcons.Speaker, "Preview ${type.label}", onPreview, tint = Wtt.Amber)
    }
}
