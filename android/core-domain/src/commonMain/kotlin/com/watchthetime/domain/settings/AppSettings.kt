package com.watchthetime.domain.settings

import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.rules.RulePreset
import kotlinx.serialization.Serializable

@Serializable
enum class FeedbackMode(val label: String) {
    SOUND_AND_HAPTICS("Sound + haptics"),
    HAPTICS_ONLY("Haptics only"),
    SILENT("Silent (visual only)"),
}

@Serializable
data class FeedbackSettings(
    val mode: FeedbackMode = FeedbackMode.SOUND_AND_HAPTICS,
    /** 0..1 */
    val masterVolume: Float = 0.9f,
    /** 0..1 scales vibration amplitude where supported. */
    val hapticStrength: Float = 1f,
    val soundOff: Set<CueType> = emptySet(),
    val hapticOff: Set<CueType> = emptySet(),
) {
    fun soundEnabled(c: CueType) = mode == FeedbackMode.SOUND_AND_HAPTICS && c !in soundOff && masterVolume > 0f
    fun hapticEnabled(c: CueType) = mode != FeedbackMode.SILENT && c !in hapticOff
}

/** Physical inputs that can be remapped. Touch swipes/long-press are structural (see docs). */
@Serializable
enum class GestureType(val label: String, val sensorBased: Boolean) {
    WRIST_FLICK("Wrist flick", true),
    DOUBLE_WRIST_TURN("Double wrist turn", true),
    SHAKE("Shake", true),
    STEM_BUTTON("Hardware button", false),
    CLOCK_DOUBLE_TAP("Double-tap screen", false),
}

@Serializable
enum class GestureAction(val label: String, val destructive: Boolean) {
    NONE("Nothing", false),
    TOGGLE_CLOCK("Start / stop clock", false),
    UNDO("Undo last action", true),
    REDO("Redo", true),
    SHOT_RESET_FULL("Shot clock full reset", false),
    SHOT_RESET_SHORT("Shot clock short reset", false),
    NEXT_SCREEN("Next screen", false),
    PREVIOUS_SCREEN("Previous screen", false),
}

@Serializable
enum class Sensitivity(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

@Serializable
enum class CrownMode(val label: String) {
    ADJUST_WHEN_STOPPED("Adjust time (clock stopped)"),
    ADJUST_ALWAYS("Adjust time (always)"),
    SWITCH_SCREENS("Switch screens"),
    OFF("Off"),
}

@Serializable
data class GestureSettings(
    val bindings: Map<GestureType, GestureAction> = mapOf(
        GestureType.WRIST_FLICK to GestureAction.TOGGLE_CLOCK,
        GestureType.DOUBLE_WRIST_TURN to GestureAction.UNDO,
        GestureType.SHAKE to GestureAction.UNDO,
        GestureType.STEM_BUTTON to GestureAction.TOGGLE_CLOCK,
        GestureType.CLOCK_DOUBLE_TAP to GestureAction.NONE,
    ),
    /** Sensor gestures are opt-in: referees move their arms constantly. */
    val enabled: Set<GestureType> = setOf(GestureType.STEM_BUTTON),
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    /** Destructive actions (undo, delete, end period, ...) ask for confirmation. */
    val safeMode: Boolean = true,
    /** Minimum gap between two triggers of any sensor gesture. */
    val debounceMs: Long = 700,
    val crown: CrownMode = CrownMode.ADJUST_WHEN_STOPPED,
    val longPressOpensEdit: Boolean = true,
    val swipeBetweenScreens: Boolean = true,
) {
    fun actionFor(g: GestureType): GestureAction =
        if (g in enabled) bindings[g] ?: GestureAction.NONE else GestureAction.NONE
}

@Serializable
data class DisplaySettings(
    val largeText: Boolean = false,
    val showTenthsInLastMinute: Boolean = true,
    /** Ambient refresh every second while the clock runs (costs battery) vs once a minute. */
    val ambientEverySecond: Boolean = true,
    /** Show the 'Assign #' chip after a score to attribute points to a player. */
    val promptScorer: Boolean = true,
    val confirmFoulType: Boolean = false,
)

@Serializable
data class GamePrefs(
    val defaultPreset: RulePreset = RulePreset.NBA,
    val shotExpiryStopsGame: Boolean = true,
    val autoPromptNextPeriod: Boolean = true,
)

@Serializable
data class AppSettings(
    val feedback: FeedbackSettings = FeedbackSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val game: GamePrefs = GamePrefs(),
    /** Last-writer-wins when phone and watch both change settings. */
    val updatedAtMs: Long = 0,
)
