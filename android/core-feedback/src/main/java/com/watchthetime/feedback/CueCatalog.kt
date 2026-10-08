package com.watchthetime.feedback

import com.watchthetime.domain.engine.CueType

/**
 * A vibration pattern: alternating off/on durations (ms) starting with an off segment, with
 * an amplitude (1..255) for every segment.
 */
data class Haptic(val timings: LongArray, val amplitudes: IntArray) {
    val totalMs: Long get() = timings.sum()

    companion object {
        /** Builds from on-pulses: (onMs, amplitude) separated by [gap] ms. */
        fun pulses(vararg on: Pair<Long, Int>, gap: Long = 80): Haptic {
            val t = mutableListOf<Long>()
            val a = mutableListOf<Int>()
            on.forEachIndexed { i, (ms, amp) ->
                t += if (i == 0) 0 else gap; a += 0
                t += ms; a += amp
            }
            return Haptic(t.toLongArray(), a.toIntArray())
        }
    }
}

data class CueSpec(val type: CueType, val soundRes: Int, val haptic: Haptic, val soundText: String, val hapticText: String)

/**
 * The single source of truth for "which moment sounds/feels like what". Every cue has a
 * distinct sound AND a distinct vibration rhythm so it can be recognised without looking.
 * docs/feedback.md is generated from the same descriptions.
 */
object CueCatalog {
    private const val L = 90   // light
    private const val M = 170  // medium
    private const val H = 255  // heavy

    val all: Map<CueType, CueSpec> = CueType.entries.associateWith { spec(it) }

    fun of(type: CueType): CueSpec = all.getValue(type)

    private fun spec(c: CueType): CueSpec = when (c) {
        CueType.CLOCK_START -> CueSpec(c, R.raw.cue_clock_start, Haptic.pulses(40L to M, 60L to H, gap = 60),
            "Two rising beeps", "short-LONG")
        CueType.CLOCK_STOP -> CueSpec(c, R.raw.cue_clock_stop, Haptic.pulses(60L to H, 40L to M, gap = 60),
            "Two falling beeps", "LONG-short")
        CueType.CLOCK_ADJUST -> CueSpec(c, R.raw.cue_clock_adjust, Haptic.pulses(15L to L),
            "Click", "tick")
        CueType.LAST_MINUTE -> CueSpec(c, R.raw.cue_last_minute, Haptic.pulses(50L to H, 50L to H, 50L to H, gap = 70),
            "Three quick beeps", "three quick")
        CueType.PERIOD_END -> CueSpec(c, R.raw.cue_period_end, Haptic.pulses(900L to H),
            "Arena horn (1.6 s)", "one long (0.9 s)")
        CueType.GAME_FINAL -> CueSpec(c, R.raw.cue_game_final, Haptic.pulses(700L to H, 1000L to H, gap = 150),
            "Double arena horn", "two long")
        CueType.PERIOD_ADVANCE -> CueSpec(c, R.raw.cue_period_advance, Haptic.pulses(40L to L, 40L to M, 60L to H, gap = 60),
            "Rising three-note chime", "rising three")
        CueType.SHOT_CLOCK_EXPIRED -> CueSpec(c, R.raw.cue_shot_clock_expired, Haptic.pulses(250L to H, 250L to H, gap = 100),
            "High short horn", "two medium-long")
        CueType.SHOT_CLOCK_RESET -> CueSpec(c, R.raw.cue_shot_clock_reset, Haptic.pulses(20L to H, 20L to L, gap = 30),
            "Click + high pip", "flick (hard-soft)")
        CueType.SCORE_1 -> CueSpec(c, R.raw.cue_score_1, Haptic.pulses(45L to M),
            "One pip", "one tap")
        CueType.SCORE_2 -> CueSpec(c, R.raw.cue_score_2, Haptic.pulses(45L to M, 45L to M),
            "Two pips", "two taps")
        CueType.SCORE_3 -> CueSpec(c, R.raw.cue_score_3, Haptic.pulses(45L to M, 45L to M, 45L to M),
            "Three pips (last higher)", "three taps")
        CueType.FOUL -> CueSpec(c, R.raw.cue_foul, Haptic.pulses(180L to M),
            "Referee whistle", "one medium")
        CueType.FOUL_FLAG -> CueSpec(c, R.raw.cue_foul_flag, Haptic.pulses(120L to H, 300L to H, gap = 100),
            "Two whistles, second long", "short-LONG strong")
        CueType.FOUL_WARNING -> CueSpec(c, R.raw.cue_foul_warning, Haptic.pulses(120L to M, 40L to H, 40L to H, gap = 70),
            "Whistle + low beep", "medium + two taps")
        CueType.FOUL_OUT -> CueSpec(c, R.raw.cue_foul_out, Haptic.pulses(100L to H, 100L to H, 600L to H, gap = 80),
            "Whistle trill", "two short + long")
        CueType.BONUS -> CueSpec(c, R.raw.cue_bonus, Haptic.pulses(60L to M, 60L to L, 60L to M, 60L to L, gap = 40),
            "Two-tone alternating beeps", "four alternating")
        CueType.TIMEOUT_START -> CueSpec(c, R.raw.cue_timeout_start, Haptic.pulses(150L to M, 300L to M, gap = 120),
            "Whistle + mid tone", "medium + long")
        CueType.TIMEOUT_END -> CueSpec(c, R.raw.cue_timeout_end, Haptic.pulses(300L to H, 300L to H, gap = 150),
            "Two short horns", "two long strong")
        CueType.UNDO -> CueSpec(c, R.raw.cue_undo, Haptic.pulses(30L to L, 30L to L, gap = 40),
            "Falling sweep", "double tick")
        CueType.REDO -> CueSpec(c, R.raw.cue_redo, Haptic.pulses(30L to L, 30L to L, 30L to L, gap = 40),
            "Rising sweep", "triple tick")
        CueType.EDIT_SAVED -> CueSpec(c, R.raw.cue_edit_saved, Haptic.pulses(70L to L),
            "Soft click", "soft hum")
        CueType.ERROR -> CueSpec(c, R.raw.cue_error, Haptic.pulses(80L to H, 80L to H, gap = 50),
            "Low double buzz", "two hard buzzes")
    }
}
