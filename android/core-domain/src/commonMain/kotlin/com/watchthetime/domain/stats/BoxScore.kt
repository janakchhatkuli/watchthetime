package com.watchthetime.domain.stats

import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.EventText
import com.watchthetime.domain.engine.Reducer
import com.watchthetime.domain.event.GameEvent
import com.watchthetime.domain.event.Score
import com.watchthetime.domain.event.kind
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState

data class BoxLine(
    val playerId: String?,
    val number: String,
    val name: String,
    val points: Int,
    val made1: Int,
    val made2: Int,
    val made3: Int,
    val personal: Int,
    val technical: Int,
    val flagrant: Int,
    val unsportsmanlike: Int,
    val countedFouls: Int,
    val disqualified: Boolean,
    val removed: Boolean,
)

data class TeamBox(
    val side: TeamSide,
    val name: String,
    val shortName: String,
    val total: Int,
    val byPeriod: List<Int>,
    val lines: List<BoxLine>,
    /** Points not attributed to a player. */
    val unassignedPoints: Int,
    val benchFouls: Int,
    val teamFoulsByPeriod: List<Int>,
    val timeoutsUsed: Int,
)

data class BoxScore(val periods: List<String>, val home: TeamBox, val away: TeamBox) {
    fun team(side: TeamSide) = if (side == TeamSide.HOME) home else away
}

object BoxScoreBuilder {

    fun build(s: GameState): BoxScore {
        val maxPeriod = maxOf(
            s.period,
            s.teams.values.flatMap { it.scoreByPeriod.keys }.maxOrNull() ?: 1,
            s.rules.regulationPeriods,
        )
        val periods = (1..maxPeriod).map { ClockFormat.periodShort(it, s.rules) }
        fun team(side: TeamSide): TeamBox {
            val t = s.team(side)
            val lines = s.players.values.filter { it.info.side == side }
                .filter { !it.removed || it.points > 0 || it.fouls.isNotEmpty() }
                .sortedWith(compareBy({ it.info.number.toIntOrNull() ?: 999 }, { it.info.number }))
                .map { p ->
                    BoxLine(
                        playerId = p.id, number = p.info.number, name = p.info.name,
                        points = p.points, made1 = p.made1, made2 = p.made2, made3 = p.made3,
                        personal = p.fouls.count { it.type == FoulType.PERSONAL },
                        technical = p.technicals, flagrant = p.flagrants, unsportsmanlike = p.unsportsmanlike,
                        countedFouls = p.countedFouls(s.rules), disqualified = p.disqualified(s.rules), removed = p.removed,
                    )
                }
            return TeamBox(
                side = side, name = t.info.name, shortName = t.info.shortName, total = t.score,
                byPeriod = (1..maxPeriod).map { t.scoreByPeriod[it] ?: 0 },
                lines = lines,
                unassignedPoints = t.score - lines.sumOf { it.points },
                benchFouls = t.fouls.count { it.playerId == null },
                teamFoulsByPeriod = (1..maxPeriod).map { s.teamFouls(side, it) },
                timeoutsUsed = t.timeoutsTaken.size,
            )
        }
        return BoxScore(periods, team(TeamSide.HOME), team(TeamSide.AWAY))
    }
}

/** RFC 4180 CSV for spreadsheets. */
object CsvExport {

    private fun esc(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun row(vararg v: Any?) = v.joinToString(",") { esc(it) }

    fun boxScore(box: BoxScore): String = buildString {
        appendLine(row("Team", *box.periods.toTypedArray(), "Total"))
        for (t in listOf(box.home, box.away)) appendLine(row(t.name, *t.byPeriod.toTypedArray(), t.total))
        appendLine()
        appendLine(row("Team", "No", "Player", "PTS", "1PT", "2PT", "3PT", "PF", "T", "F", "U", "Counted fouls", "Status"))
        for (t in listOf(box.home, box.away)) {
            for (l in t.lines) appendLine(
                row(
                    t.shortName, l.number, l.name, l.points, l.made1, l.made2, l.made3, l.personal,
                    l.technical, l.flagrant, l.unsportsmanlike, l.countedFouls,
                    when { l.disqualified -> "DQ"; l.removed -> "Removed"; else -> "" },
                )
            )
            if (t.unassignedPoints != 0) appendLine(row(t.shortName, "", "(unassigned)", t.unassignedPoints))
            appendLine(row(t.shortName, "", "Bench/team fouls", "", "", "", "", t.benchFouls))
        }
    }

    fun events(events: Collection<GameEvent>, s: GameState): String = buildString {
        appendLine(row("Seq", "Period", "Clock", "Type", "Description", "Team", "Points", "Deleted", "Revision", "Wall time (ms)", "Event id"))
        for (e in events.sortedWith(Reducer.ORDER)) {
            val side = when (val p = e.payload) {
                is Score -> p.side.name
                is com.watchthetime.domain.event.Foul -> p.side.name
                else -> ""
            }
            val pts = (e.payload as? Score)?.points ?: ""
            appendLine(
                row(
                    e.seq, ClockFormat.periodShort(e.period, s.rules), ClockFormat.plain(e.gameClockMs, true),
                    e.payload.kind, EventText.describe(e, s), side, pts, e.deleted, e.rev, e.at.wallMs, e.id,
                )
            )
        }
    }

    fun full(events: Collection<GameEvent>, s: GameState): String = buildString {
        appendLine(row("${s.team(TeamSide.HOME).info.name} ${s.team(TeamSide.HOME).score} - ${s.team(TeamSide.AWAY).score} ${s.team(TeamSide.AWAY).info.name}"))
        appendLine()
        append(boxScore(BoxScoreBuilder.build(s)))
        appendLine()
        append(events(events, s))
    }
}
