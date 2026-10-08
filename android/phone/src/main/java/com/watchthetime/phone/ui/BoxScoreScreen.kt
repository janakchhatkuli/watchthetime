package com.watchthetime.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.HRule
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.SegmentText
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.stats.BoxScoreBuilder
import com.watchthetime.domain.stats.TeamBox
import com.watchthetime.phone.Exporter

@Composable
private fun C(text: String, w: Dp, color: Color = Wtt.OffWhite, bold: Boolean = false, align: TextAlign = TextAlign.End) {
    Text(
        text, Modifier.width(w), color = color, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun BoxScoreScreen(gameId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val live = rememberLiveGame(gameId)
    if (live == null) { WttScreen("Box score", onBack) {}; return }
    val s = live.state
    val box = BoxScoreBuilder.build(s)

    WttScreen("Box score", onBack, subtitle = if (s.finalized) "Final" else ClockFormat.periodLong(s.period, s.rules)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            // Big score
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                for ((i, t) in listOf(box.home, box.away).withIndex()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamTag(t.shortName, s.team(t.side).info.colorArgb)
                        Spacer(Modifier.height(6.dp))
                        SegmentText(pad3(t.total), 44.sp, color = if (s.finalized) Wtt.OffWhite else Wtt.Amber, spoken = "${t.name} ${t.total}")
                    }
                    if (i == 0) Text("–", style = MaterialTheme.typography.displaySmall, color = Wtt.Muted)
                }
            }
            HRule()
            SectionLabel("By period")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        C("", 72.dp, align = TextAlign.Start)
                        for (p in box.periods) C(p, 44.dp, Wtt.Muted, true)
                        C("T", 52.dp, Wtt.Amber, true)
                    }
                    for (t in listOf(box.home, box.away)) {
                        Row(Modifier.padding(vertical = 4.dp)) {
                            C(t.shortName, 72.dp, bold = true, align = TextAlign.Start)
                            for (v in t.byPeriod) C("$v", 44.dp)
                            C("${t.total}", 52.dp, Wtt.Amber, true)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    for (t in listOf(box.home, box.away)) {
                        Row(Modifier.padding(vertical = 2.dp)) {
                            C("${t.shortName} TF", 72.dp, Wtt.Muted, align = TextAlign.Start)
                            for (v in t.teamFoulsByPeriod) C("$v", 44.dp, Wtt.Muted)
                            C("", 52.dp)
                        }
                    }
                }
            }
            for (t in listOf(box.home, box.away)) TeamTable(t, s.team(t.side).info.colorArgb)
            Spacer(Modifier.height(16.dp))
        }
        HRule()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WttButton("CSV", { Exporter.shareCsv(ctx, live.events, s) }, Modifier.weight(1f), icon = WttIcons.Share, kind = ButtonKind.SECONDARY, minHeight = 52.dp)
            WttButton("PDF", { Exporter.sharePdf(ctx, live.events, s) }, Modifier.weight(1f), icon = WttIcons.Share, kind = ButtonKind.PRIMARY, minHeight = 52.dp)
        }
    }
}

@Composable
private fun TeamTable(t: TeamBox, color: Long) {
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth().background(Wtt.argb(color)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(t.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = Wtt.onTeam(color))
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            Row(Modifier.padding(vertical = 6.dp)) {
                C("#", 36.dp, Wtt.Muted, true, TextAlign.Start)
                C("PLAYER", 120.dp, Wtt.Muted, true, TextAlign.Start)
                for (h in listOf("PTS", "1", "2", "3", "PF", "T", "F", "U")) C(h, 36.dp, if (h == "PTS") Wtt.Amber else Wtt.Muted, true)
            }
            HRule()
            for (l in t.lines) {
                val col = if (l.disqualified) Wtt.Red else if (l.removed) Wtt.Disabled else Wtt.OffWhite
                Row(Modifier.padding(vertical = 5.dp)) {
                    C(l.number, 36.dp, col, true, TextAlign.Start)
                    C(l.name.ifBlank { "—" } + if (l.disqualified) " · DQ" else "", 120.dp, col, align = TextAlign.Start)
                    C("${l.points}", 36.dp, Wtt.Amber, true)
                    C("${l.made1}", 36.dp, col)
                    C("${l.made2}", 36.dp, col)
                    C("${l.made3}", 36.dp, col)
                    C("${l.countedFouls}", 36.dp, col)
                    C("${l.technical}", 36.dp, col)
                    C("${l.flagrant}", 36.dp, col)
                    C("${l.unsportsmanlike}", 36.dp, col)
                }
            }
            HRule()
            if (t.unassignedPoints != 0) Row(Modifier.padding(vertical = 5.dp)) {
                C("", 36.dp); C("Team / unassigned", 120.dp, Wtt.Muted, align = TextAlign.Start); C("${t.unassignedPoints}", 36.dp, Wtt.Amber)
            }
            Row(Modifier.padding(vertical = 5.dp)) {
                C("", 36.dp); C("Bench fouls", 120.dp, Wtt.Muted, align = TextAlign.Start)
                C("", 36.dp); C("", 36.dp); C("", 36.dp); C("", 36.dp); C("${t.benchFouls}", 36.dp, Wtt.Muted)
            }
            Row(Modifier.padding(vertical = 5.dp)) {
                C("", 36.dp); C("Timeouts used", 120.dp, Wtt.Muted, align = TextAlign.Start); C("${t.timeoutsUsed}", 36.dp, Wtt.Muted)
            }
        }
    }
}
