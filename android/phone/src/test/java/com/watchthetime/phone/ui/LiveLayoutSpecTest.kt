package com.watchthetime.phone.ui

import com.watchthetime.phone.ui.LiveLayoutSpec.Companion.MIN_TOUCH
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Landscape sizes in dp after insets. Phones lose up to ~48 dp of width to a display cutout
 * in landscape, so both raw and cut widths are covered.
 */
class LiveLayoutSpecTest {

    private val sizes = listOf(
        "small phone (cutout)" to (560f to 320f),
        "small phone" to (640f to 360f),
        "phone 360 cutout" to (592f to 360f),
        "Pixel-class" to (732f to 412f),
        "large phone" to (915f to 412f),
        "large phone cutout" to (867f to 412f),
        "tall phone" to (800f to 360f),
        "foldable inner" to (841f to 701f),
        "7-inch tablet" to (1024f to 600f),
        "10-inch tablet" to (1280f to 800f),
        "12-inch tablet" to (1366f to 1024f),
    )

    private fun check(name: String, w: Float, h: Float, scoreButtons: Int) {
        val s = LiveLayoutSpec.compute(w, h, scoreButtons)
        val tag = "$name ${w.toInt()}x${h.toInt()} (${scoreButtons} score buttons)"
        assertTrue("$tag: buttons ≥ 48dp (was ${s.buttonWidth})", s.buttonWidth >= MIN_TOUCH)
        assertTrue("$tag: strip fits width (${s.stripContentWidth} > $w)", s.stripContentWidth <= w + 0.01f)
        assertTrue("$tag: panels fit width", 2 * s.sideWidth + s.centerWidth <= w + 0.01f)
        assertTrue("$tag: side stack fits (${s.sideStackHeight} > ${s.middleHeight})", s.sideStackHeight <= s.middleHeight + 0.01f)
        assertTrue("$tag: center stack fits (${s.centerStackHeight} > ${s.middleHeight})", s.centerStackHeight <= s.middleHeight + 0.01f)
        assertTrue("$tag: score '888' fits side", LiveLayoutSpec.SCORE_EM * s.scoreFontDp <= s.sideWidth - 2 * LiveLayoutSpec.SIDE_PAD + 0.01f)
        assertTrue("$tag: clock '88:88' fits center", LiveLayoutSpec.CLOCK_EM * s.clockFontDp <= s.centerWidth - 2 * LiveLayoutSpec.CENTER_PAD + 0.01f)
        // Shot row: shot digits + two 56dp reset buttons + gaps.
        assertTrue("$tag: shot row fits center", LiveLayoutSpec.SHOT_EM * s.shotFontDp + 2 * 56f + 3 * 8f + 24f <= s.centerWidth)
        assertTrue("$tag: clock is the hero (${s.clockFontDp} vs ${s.scoreFontDp})", s.clockFontDp >= s.scoreFontDp)
        assertTrue("$tag: clock is readable (${s.clockFontDp})", s.clockFontDp >= 64f)
    }

    @Test fun fiveOnFiveFitsEverySize() = sizes.forEach { (n, wh) -> check(n, wh.first, wh.second, 3) }

    @Test fun threeByThreeFitsEverySize() = sizes.forEach { (n, wh) -> check(n, wh.first, wh.second, 2) }

    @Test fun clockGrowsWithTheScreen() {
        val small = LiveLayoutSpec.compute(640f, 360f)
        val tablet = LiveLayoutSpec.compute(1280f, 800f)
        assertTrue(tablet.clockFontDp > small.clockFontDp * 1.5f)
    }

    @Test fun clockStringWidths() {
        assertTrue(LiveLayoutSpec.widthEm("12:00") == LiveLayoutSpec.CLOCK_EM)
        assertTrue(LiveLayoutSpec.widthEm("59.9") < LiveLayoutSpec.CLOCK_EM)
    }
}
