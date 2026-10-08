package com.watchthetime.phone

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.EventText
import com.watchthetime.domain.event.GameEvent
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState
import com.watchthetime.domain.stats.BoxScoreBuilder
import com.watchthetime.domain.stats.CsvExport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes CSV / PDF reports to the cache and opens the system share sheet. */
object Exporter {

    private fun baseName(s: GameState): String {
        val h = s.team(TeamSide.HOME).info.shortName
        val a = s.team(TeamSide.AWAY).info.shortName
        val d = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "${h}-vs-${a}_$d".replace(Regex("[^A-Za-z0-9_-]"), "")
    }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun shareCsv(ctx: Context, events: List<GameEvent>, s: GameState) {
        val f = File(dir(ctx), baseName(s) + ".csv")
        f.writeText("\uFEFF" + CsvExport.full(events, s)) // BOM so Excel detects UTF-8
        share(ctx, f, "text/csv")
    }

    fun sharePdf(ctx: Context, events: List<GameEvent>, s: GameState) {
        val f = File(dir(ctx), baseName(s) + ".pdf")
        writePdf(ctx, f, events, s)
        share(ctx, f, "application/pdf")
    }

    private fun share(ctx: Context, f: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, f.nameWithoutExtension)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Export game").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- PDF -----------------------------------------------------------------------------

    private const val W = 595 // A4 @ 72 dpi
    private const val H = 842
    private const val M = 40f

    private class Writer(ctx: Context, val doc: PdfDocument) {
        val bold: Typeface = ResourcesCompat.getFont(ctx, com.watchthetime.brand.R.font.barlow_condensed_bold) ?: Typeface.DEFAULT_BOLD
        val regular: Typeface = ResourcesCompat.getFont(ctx, com.watchthetime.brand.R.font.barlow_condensed_semibold) ?: Typeface.DEFAULT
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
        }

        fun ensure(h: Float) { if (y + h > H - M) newPage() }

        fun paint(size: Float, b: Boolean = false, color: Int = 0xFF0A0A0A.toInt()) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = if (b) bold else regular; this.color = color
        }

        fun text(t: String, x: Float, size: Float, b: Boolean = false, color: Int = 0xFF0A0A0A.toInt(), right: Boolean = false) {
            val p = paint(size, b, color)
            if (right) p.textAlign = Paint.Align.RIGHT
            page.canvas.drawText(t, x, y, p)
        }

        fun rule(color: Int = 0xFF0A0A0A.toInt(), width: Float = 1f) {
            page.canvas.drawRect(M, y, W - M, y + width, Paint().apply { this.color = color })
        }

        fun row(cols: List<String>, xs: List<Float>, size: Float = 10f, b: Boolean = false) {
            ensure(size + 6)
            y += size + 4
            cols.forEachIndexed { i, c -> text(c, xs[i], size, b, right = i > 1) }
        }
    }

    private fun writePdf(ctx: Context, f: File, events: List<GameEvent>, s: GameState) {
        val doc = PdfDocument()
        val w = Writer(ctx, doc)
        w.newPage()
        val box = BoxScoreBuilder.build(s)
        val amber = 0xFFFF8A00.toInt()

        // Header bar
        w.page.canvas.drawRect(0f, 0f, W.toFloat(), 8f, Paint().apply { color = amber })
        w.y = M + 10
        w.text((s.title.ifBlank { "GAME REPORT" }).uppercase(), M, 12f, true, 0xFF8A8A85.toInt())
        w.y += 30
        w.text("${box.home.name.uppercase()}  ${box.home.total}  –  ${box.away.total}  ${box.away.name.uppercase()}", M, 26f, true)
        w.y += 18
        val status = if (s.finalized) "FINAL" else "${ClockFormat.periodLong(s.period, s.rules)} · ${ClockFormat.plain(s.clock.baseMs, true)}"
        w.text("$status · ${s.rules.preset.label} rules · exported ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}", M, 10f, false, 0xFF555555.toInt())
        w.y += 12
        w.rule(amber, 2f)

        // Line score
        w.y += 8
        val pcols = box.periods
        val lxs = listOf(M, M + 120) + pcols.indices.map { M + 170 + it * 40f } + listOf(M + 170 + pcols.size * 40f + 10)
        val adjXs = lxs.mapIndexed { i, x -> if (i >= 2) x + 30 else x }
        w.row(listOf("TEAM", "") + pcols + "T", adjXs, 11f, true)
        for (t in listOf(box.home, box.away)) {
            w.row(listOf(t.name, "") + t.byPeriod.map { it.toString() } + t.total.toString(), adjXs, 11f)
        }
        w.y += 6
        w.row(listOf("TEAM FOULS", "") + box.home.teamFoulsByPeriod.map { it.toString() } + "", adjXs, 9f)
        w.y -= 0
        w.row(listOf("", "") + box.away.teamFoulsByPeriod.map { it.toString() } + "", adjXs, 9f)

        // Player tables
        val xs = listOf(M, M + 30, M + 250, M + 290, M + 330, M + 370, M + 410, M + 440, M + 470, M + 500)
        for (t in listOf(box.home, box.away)) {
            w.ensure(60f)
            w.y += 22
            w.text(t.name.uppercase(), M, 14f, true)
            w.y += 4
            w.rule()
            w.row(listOf("#", "PLAYER", "PTS", "1PT", "2PT", "3PT", "PF", "T", "F", "U"), xs, 10f, true)
            for (l in t.lines) {
                val name = l.name + when { l.disqualified -> "  (DQ)"; l.removed -> "  (removed)"; else -> "" }
                w.row(
                    listOf(l.number, name, "${l.points}", "${l.made1}", "${l.made2}", "${l.made3}", "${l.countedFouls}", "${l.technical}", "${l.flagrant}", "${l.unsportsmanlike}"),
                    xs,
                )
            }
            if (t.unassignedPoints != 0) w.row(listOf("", "Unassigned", "${t.unassignedPoints}", "", "", "", "", "", "", ""), xs)
            w.row(listOf("", "Bench / team fouls", "", "", "", "", "${t.benchFouls}", "", "", ""), xs)
            w.row(listOf("", "Timeouts used", "${t.timeoutsUsed}", "", "", "", "", "", "", ""), xs)
        }

        // Event log
        w.ensure(60f)
        w.y += 26
        w.text("PLAY-BY-PLAY", M, 14f, true)
        w.y += 4
        w.rule()
        val exs = listOf(M, M + 70, M + 120)
        for (e in events.filter { !it.deleted }) {
            w.ensure(14f)
            w.y += 13
            w.text(ClockFormat.periodShort(e.period, s.rules), exs[0], 9f)
            w.text(ClockFormat.plain(e.gameClockMs, true), exs[1], 9f)
            w.text(EventText.describe(e, s), exs[2], 9f)
        }

        doc.finishPage(w.page)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
