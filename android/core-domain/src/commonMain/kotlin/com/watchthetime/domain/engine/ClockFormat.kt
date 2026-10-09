package com.watchthetime.domain.engine

import com.watchthetime.domain.rules.PeriodFormat
import com.watchthetime.domain.rules.Rules

/**
 * Display formatting for scoreboard digits.
 *
 * Output is designed for the DSEG7 seven-segment font: '!' is DSEG's blank glyph with digit
 * width, so strings keep a constant width and the layout never jitters. [ghost] produces the
 * matching "all segments lit" template that is drawn dimmed behind the live digits.
 */
object ClockFormat {

    /**
     * Game clock. "12:00", "!9:59" for minutes; "59.9" in the last minute when [tenths].
     * Values are floored (a clock started at 12:00 immediately reads 11:59).
     */
    fun game(ms: Long, tenths: Boolean = true, lastMinuteMs: Long = 60_000): String {
        val v = ms.coerceAtLeast(0)
        return if (tenths && v < lastMinuteMs) {
            val s = v / 1000
            val t = (v % 1000) / 100
            "${pad2(s)}.$t"
        } else {
            val totalSec = v / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            "${pad2(m)}:${s.toString().padStart(2, '0')}"
        }
    }

    /** Plain text clock for logs/exports (no DSEG blanks): "11:59", "0:42.3". */
    fun plain(ms: Long, tenths: Boolean = false): String {
        val v = ms.coerceAtLeast(0)
        val totalSec = v / 1000
        val base = "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
        return if (tenths && v < 60_000) "$base.${(v % 1000) / 100}" else base
    }

    /** Shot clock: "24", "!9", tenths under 5 seconds ("4.9"). */
    fun shot(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        return if (v < 5_000) "${v / 1000}.${(v % 1000) / 100}" else pad2(v / 1000)
    }

    /** Timeout countdown: "1:15" / "!45". */
    fun countdown(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0) + 999) / 1000 // ceil so it ends exactly at 0
        return if (totalSec >= 60) "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}" else pad2(totalSec)
    }

    /** Replace digits/blanks with '8' to get the unlit-segment backdrop. */
    fun ghost(display: String): String = display.map { if (it.isDigit() || it == '!') '8' else it }.joinToString("")

    private fun pad2(n: Long) = if (n < 10) "!$n" else n.toString()

    /** Strip DSEG blanks for screen readers / plain text. */
    fun spoken(display: String) = display.replace("!", "")

    fun periodShort(period: Int, rules: Rules): String = when {
        rules.isOvertime(period) && rules.regulationPeriods == 1 -> if (period == 2) "OT" else "OT${period - 1}"
        rules.isOvertime(period) -> "OT${period - rules.regulationPeriods}"
        rules.format == PeriodFormat.SINGLE -> "REG"
        rules.format == PeriodFormat.HALVES -> "H$period"
        else -> "Q$period"
    }

    fun periodLong(period: Int, rules: Rules): String = when {
        rules.isOvertime(period) && rules.regulationPeriods == 1 -> "OVERTIME"
        rules.isOvertime(period) -> "OVERTIME ${period - rules.regulationPeriods}"
        rules.format == PeriodFormat.SINGLE -> "REGULAR TIME"
        rules.format == PeriodFormat.HALVES -> if (period == 1) "1ST HALF" else "2ND HALF"
        else -> "${ordinal(period)} QUARTER"
    }

    private fun ordinal(n: Int) = when (n) { 1 -> "1ST"; 2 -> "2ND"; 3 -> "3RD"; else -> "${n}TH" }
}
