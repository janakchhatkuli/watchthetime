package com.watchthetime.phone.ui

/**
 * Pure sizing for the landscape live screen. No Android/Compose types, so it is unit-tested on
 * the JVM at many screen sizes (see LiveLayoutSpecTest): every box must fit, nothing may
 * overlap, and every touch target is at least [MIN_TOUCH] dp.
 *
 * Screen structure (all values in dp):
 * ```
 * ┌ side ─────────┬ center ──────────────────────┬ side ─────────┐
 * │ Q · TEAM      │ banner / assign chip          │ TEAM · menu   │  topRow / banner
 * │ 087           │        12:00                  │ 079           │  score / clock
 * │ F 3  BONUS    │ STOPPED · TAP TO START        │ F 5  BONUS    │  stats / status
 * │ [T/O 2]       │ [24] shot [14]  | timeout bar │ [T/O 3]       │  T/O / shot row
 * ├───────────────┴───────────────────────────────┴───────────────┤
 * │ [+1][+2][+3][FOUL][⋯]      [↶][↷]      [+1][+2][+3][FOUL][⋯] │  strip
 * └────────────────────────────────────────────────────────────────┘
 * ```
 */
data class LiveLayoutSpec(
    val width: Float,
    val height: Float,
    val stripHeight: Float,
    val buttonWidth: Float,
    val buttonGap: Float,
    /** Buttons per team group in the strip (score buttons + FOUL + MORE). */
    val teamButtons: Int,
    val showRedo: Boolean,
    val sideWidth: Float,
    val centerWidth: Float,
    val middleHeight: Float,
    /** Font sizes of the seven-segment digits, in dp (converted to sp with fontScale undone). */
    val scoreFontDp: Float,
    val clockFontDp: Float,
    val shotFontDp: Float,
) {
    val centerGroupWidth: Float get() = (if (showRedo) 2 else 1) * MIN_TOUCH + (if (showRedo) buttonGap else 0f)
    val teamGroupWidth: Float get() = teamButtons * buttonWidth + (teamButtons - 1) * buttonGap
    val stripContentWidth: Float get() = 2 * teamGroupWidth + centerGroupWidth + 2 * STRIP_PAD + 2 * buttonGap

    /** Side panel stack height (top row + score + stats + timeout button + gaps). */
    val sideStackHeight: Float get() = TOP_ROW + scoreFontDp * LINE + STATS_ROW + MIN_TOUCH + SIDE_GAPS

    /** Center stack height (banner + clock + status + shot/timeout row + gaps). */
    val centerStackHeight: Float get() = BANNER + clockFontDp * LINE + STATUS + SHOT_ROW + CENTER_GAPS

    companion object {
        const val MIN_TOUCH = 48f
        const val MAX_BUTTON = 76f
        const val STRIP = 56f
        const val STRIP_PAD = 6f
        const val TOP_ROW = 48f
        const val STATS_ROW = 28f
        const val SIDE_GAPS = 12f
        const val BANNER = 48f
        const val STATUS = 22f
        const val SHOT_ROW = 52f
        const val CENTER_GAPS = 8f
        const val SIDE_PAD = 8f
        const val CENTER_PAD = 8f

        /** Line height factor for DSEG7 (ascent 1.0em, no descent) with a safety margin. */
        const val LINE = 1.2f

        /** DSEG7 Classic advance widths in em (measured from the TTF: digits/blank 0.816, ':' 0.2, '.' 0). */
        const val DIGIT_EM = 0.816f
        const val COLON_EM = 0.2f

        /** Widest game clock string: "88:88". */
        val CLOCK_EM = 4 * DIGIT_EM + COLON_EM
        /** Score: three digits. */
        val SCORE_EM = 3 * DIGIT_EM
        /** Shot clock: two digits. */
        val SHOT_EM = 2 * DIGIT_EM

        fun widthEm(text: String): Float = text.sumOf {
            when (it) {
                ':' -> COLON_EM.toDouble()
                '.' -> 0.0
                else -> DIGIT_EM.toDouble()
            }
        }.toFloat()

        /**
         * @param width available width in dp (after cutout/system insets)
         * @param height available height in dp
         * @param scoreButtons 3 for 5-on-5 (+1 +2 +3), 2 for 3x3 (+1 +2)
         */
        fun compute(width: Float, height: Float, scoreButtons: Int = 3): LiveLayoutSpec {
            val teamButtons = scoreButtons + 2
            // Strip: try comfortable gaps and redo first, then shrink.
            var gap = 6f
            var showRedo = true
            fun buttonFor(gap: Float, redo: Boolean): Float {
                val center = (if (redo) 2 * MIN_TOUCH + gap else MIN_TOUCH)
                val free = width - 2 * STRIP_PAD - center - 2 * gap - 2 * (teamButtons - 1) * gap
                return (free / (2 * teamButtons)).coerceAtMost(MAX_BUTTON)
            }
            var button = buttonFor(gap, showRedo)
            if (button < MIN_TOUCH) { showRedo = false; button = buttonFor(gap, showRedo) }
            if (button < MIN_TOUCH) { gap = 2f; button = buttonFor(gap, showRedo) }
            button = button.coerceAtLeast(MIN_TOUCH) // below ~540 dp the test reports the overflow

            val middle = height - STRIP
            val side = (width * 0.24f).coerceIn(140f, 320f)
            val center = width - 2 * side

            val scoreByWidth = (side - 2 * SIDE_PAD) / SCORE_EM
            val scoreByHeight = (middle - TOP_ROW - STATS_ROW - MIN_TOUCH - SIDE_GAPS) / LINE
            val score = minOf(scoreByWidth, scoreByHeight, 150f)

            val clockByWidth = (center - 2 * CENTER_PAD) / CLOCK_EM
            val clockByHeight = (middle - BANNER - STATUS - SHOT_ROW - CENTER_GAPS) / LINE
            val clock = minOf(clockByWidth, clockByHeight, 260f)

            val shot = minOf(36f, (SHOT_ROW - 8f) / LINE)

            return LiveLayoutSpec(
                width = width, height = height, stripHeight = STRIP, buttonWidth = button, buttonGap = gap,
                teamButtons = teamButtons, showRedo = showRedo, sideWidth = side, centerWidth = center,
                middleHeight = middle, scoreFontDp = score, clockFontDp = clock, shotFontDp = shot,
            )
        }
    }
}
