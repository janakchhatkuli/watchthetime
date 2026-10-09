package com.watchthetime.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watchthetime.brand.ButtonKind
import com.watchthetime.brand.SectionLabel
import com.watchthetime.brand.Stepper
import com.watchthetime.brand.Wtt
import com.watchthetime.brand.WttButton
import com.watchthetime.brand.WttChip
import com.watchthetime.brand.WttIconButton
import com.watchthetime.brand.WttIcons
import com.watchthetime.data.SavedPlayer
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.rules.ClockMode
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.PeriodFormat
import com.watchthetime.domain.rules.RulePreset
import com.watchthetime.domain.rules.Rules
import java.util.UUID

private fun cycle(list: List<Int>, cur: Int, dir: Int): Int {
    val i = list.indexOfFirst { it >= cur }.let { if (it < 0) list.lastIndex else it }
    val exact = list.getOrNull(i) == cur
    val next = if (dir > 0) (if (exact) i + 1 else i) else i - 1
    return list[next.coerceIn(0, list.lastIndex)]
}

/** Clock mode picker (stopping / running) with the per-mode late-game option. */
@Composable
fun ClockPolicyEditor(policy: ClockPolicy, rules: Rules, onChange: (ClockPolicy) -> Unit) {
    SectionLabel("Clock mode")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in ClockMode.entries) {
            WttChip(m.label, policy.mode == m, { onChange(policy.copy(mode = m)) }, Modifier.weight(1f))
        }
    }
    Text(policy.mode.detail, style = MaterialTheme.typography.bodySmall, color = Wtt.Muted, modifier = Modifier.padding(top = 6.dp))
    when (policy.mode) {
        ClockMode.STOPPING -> ToggleRow(
            "Scores stop the clock only late in the game", policy.scoresStopOnlyLate,
            { onChange(policy.copy(scoresStopOnlyLate = it)) },
            "Official style: ${rules.lateWindowText()}. Fouls, timeouts, free throws and substitutions always stop it.",
        )
        ClockMode.RUNNING -> ToggleRow(
            "Stop the clock late in the game", policy.runningStopsLate,
            { onChange(policy.copy(runningStopsLate = it)) },
            "Behaves like stopping time during the ${rules.lateWindowText()}.",
        )
    }
}

/** Full rules editor. Any manual change marks the rules as CUSTOM. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesEditor(rules: Rules, onChange: (Rules) -> Unit, lockedFormat: Boolean = false) {
    fun set(r: Rules) = onChange(r.copy(preset = RulePreset.CUSTOM).validated())

    SectionLabel("Preset")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in RulePreset.entries.filter { it != RulePreset.CUSTOM }) {
            WttChip(p.label, rules.preset == p, { onChange(Rules.preset(p)) })
        }
        if (rules.preset == RulePreset.CUSTOM) WttChip("Custom", true, {})
    }

    SectionLabel("Periods")
    if (!lockedFormat) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WttChip("4 quarters", rules.format == PeriodFormat.QUARTERS, { set(rules.copy(format = PeriodFormat.QUARTERS)) })
            WttChip("2 halves", rules.format == PeriodFormat.HALVES, { set(rules.copy(format = PeriodFormat.HALVES)) })
        }
    }
    val pMin = (rules.periodLengthMs / Rules.MIN).toInt()
    Stepper("Period length", "$pMin min",
        { set(rules.copy(periodLengthMs = cycle(Rules.PERIOD_LENGTH_CHOICES_MIN, pMin, -1) * Rules.MIN)) },
        { set(rules.copy(periodLengthMs = cycle(Rules.PERIOD_LENGTH_CHOICES_MIN, pMin, 1) * Rules.MIN)) })
    val oMin = (rules.overtimeLengthMs / Rules.MIN).toInt()
    Stepper("Overtime length", "$oMin min",
        { set(rules.copy(overtimeLengthMs = cycle(Rules.OVERTIME_LENGTH_CHOICES_MIN, oMin, -1) * Rules.MIN)) },
        { set(rules.copy(overtimeLengthMs = cycle(Rules.OVERTIME_LENGTH_CHOICES_MIN, oMin, 1) * Rules.MIN)) })

    SectionLabel("Player fouls")
    Stepper("Foul-out at", "${rules.playerFoulLimit}",
        { set(rules.copy(playerFoulLimit = rules.playerFoulLimit - 1, playerFoulWarnAt = (rules.playerFoulLimit - 2).coerceAtLeast(0))) },
        { set(rules.copy(playerFoulLimit = rules.playerFoulLimit + 1, playerFoulWarnAt = rules.playerFoulLimit)) })
    Stepper("Warn at", if (rules.playerFoulWarnAt == 0) "OFF" else "${rules.playerFoulWarnAt}",
        { set(rules.copy(playerFoulWarnAt = (rules.playerFoulWarnAt - 1).coerceAtLeast(0))) },
        { set(rules.copy(playerFoulWarnAt = rules.playerFoulWarnAt + 1)) })
    Stepper("Technicals to eject", if (rules.technicalsForEjection == 0) "OFF" else "${rules.technicalsForEjection}",
        { set(rules.copy(technicalsForEjection = (rules.technicalsForEjection - 1).coerceAtLeast(0))) },
        { set(rules.copy(technicalsForEjection = (rules.technicalsForEjection + 1).coerceAtMost(5))) })
    Stepper("Flagrants to eject", if (rules.flagrantsForEjection == 0) "OFF" else "${rules.flagrantsForEjection}",
        { set(rules.copy(flagrantsForEjection = (rules.flagrantsForEjection - 1).coerceAtLeast(0))) },
        { set(rules.copy(flagrantsForEjection = (rules.flagrantsForEjection + 1).coerceAtMost(5))) })
    Stepper("Unsportsmanlike to eject", if (rules.unsportsmanlikeForEjection == 0) "OFF" else "${rules.unsportsmanlikeForEjection}",
        { set(rules.copy(unsportsmanlikeForEjection = (rules.unsportsmanlikeForEjection - 1).coerceAtLeast(0))) },
        { set(rules.copy(unsportsmanlikeForEjection = (rules.unsportsmanlikeForEjection + 1).coerceAtMost(5))) })
    ToggleRow("Technical counts as personal foul", rules.technicalCountsAsPersonal, { set(rules.copy(technicalCountsAsPersonal = it)) })
    ToggleRow("Technical + unsportsmanlike ejects", rules.technicalPlusUnsportsmanlikeEjects, { set(rules.copy(technicalPlusUnsportsmanlikeEjects = it)) }, "FIBA")

    SectionLabel("Team fouls / bonus")
    Stepper("Bonus from team foul", "${rules.bonusAt}",
        { set(rules.copy(bonusAt = rules.bonusAt - 1)) }, { set(rules.copy(bonusAt = rules.bonusAt + 1)) })
    Stepper("Double bonus from", if (rules.doubleBonusAt == 0) "OFF" else "${rules.doubleBonusAt}",
        { set(rules.copy(doubleBonusAt = (rules.doubleBonusAt - 1).let { if (it <= rules.bonusAt) 0 else it })) },
        { set(rules.copy(doubleBonusAt = if (rules.doubleBonusAt == 0) rules.bonusAt + 1 else rules.doubleBonusAt + 1)) })
    Stepper("Overtime bonus from", "${rules.overtimeBonusAt}",
        { set(rules.copy(overtimeBonusAt = rules.overtimeBonusAt - 1)) }, { set(rules.copy(overtimeBonusAt = rules.overtimeBonusAt + 1)) })
    ToggleRow("Overtime continues last period's fouls", rules.overtimeFoulsCarry, { set(rules.copy(overtimeFoulsCarry = it)) })
    ToggleRow("Last 2 minutes rule", rules.lastTwoMinutesRule, { set(rules.copy(lastTwoMinutesRule = it)) },
        "NBA: bonus from the 2nd team foul in the last 2:00 of a period")
    ToggleRow("Technicals count as team fouls", rules.technicalCountsAsTeamFoul, { set(rules.copy(technicalCountsAsTeamFoul = it)) })

    SectionLabel("Timeouts")
    Stepper(if (rules.format == PeriodFormat.QUARTERS && rules.timeoutsSecondHalf == 0) "Timeouts per game" else "Timeouts 1st half", "${rules.timeoutsFirstHalf}",
        { set(rules.copy(timeoutsFirstHalf = (rules.timeoutsFirstHalf - 1).coerceAtLeast(0))) },
        { set(rules.copy(timeoutsFirstHalf = (rules.timeoutsFirstHalf + 1).coerceAtMost(20))) })
    Stepper("Timeouts 2nd half (extra)", "${rules.timeoutsSecondHalf}",
        { set(rules.copy(timeoutsSecondHalf = (rules.timeoutsSecondHalf - 1).coerceAtLeast(0))) },
        { set(rules.copy(timeoutsSecondHalf = (rules.timeoutsSecondHalf + 1).coerceAtMost(20))) })
    Stepper("Timeouts per overtime", "${rules.timeoutsPerOvertime}",
        { set(rules.copy(timeoutsPerOvertime = (rules.timeoutsPerOvertime - 1).coerceAtLeast(0))) },
        { set(rules.copy(timeoutsPerOvertime = (rules.timeoutsPerOvertime + 1).coerceAtMost(10))) })
    Stepper("Timeout length", "${rules.timeoutLengthMs / 1000} s",
        { set(rules.copy(timeoutLengthMs = rules.timeoutLengthMs - 15 * Rules.SEC)) },
        { set(rules.copy(timeoutLengthMs = rules.timeoutLengthMs + 15 * Rules.SEC)) })
    ToggleRow("Unused timeouts carry to 2nd half", rules.timeoutsCarryToSecondHalf, { set(rules.copy(timeoutsCarryToSecondHalf = it)) })
    ToggleRow("Unused timeouts carry into overtime", rules.timeoutsCarryIntoOvertime, { set(rules.copy(timeoutsCarryIntoOvertime = it)) })

    SectionLabel("Shot clock")
    ToggleRow("Shot clock", rules.shotClockEnabled, { set(rules.copy(shotClockEnabled = it)) })
    if (rules.shotClockEnabled) {
        Stepper("Full reset", "${rules.shotClockFullMs / 1000} s",
            { set(rules.copy(shotClockFullMs = rules.shotClockFullMs - Rules.SEC)) },
            { set(rules.copy(shotClockFullMs = rules.shotClockFullMs + Rules.SEC)) })
        Stepper("Short reset", "${rules.shotClockShortMs / 1000} s",
            { set(rules.copy(shotClockShortMs = rules.shotClockShortMs - Rules.SEC)) },
            { set(rules.copy(shotClockShortMs = rules.shotClockShortMs + Rules.SEC)) })
    }
    Spacer(Modifier.padding(8.dp))
}

/** Name / short name / colour editor. */
@Composable
fun TeamInfoEditor(info: TeamInfo, onChange: (TeamInfo) -> Unit) {
    WttTextField(info.name, { onChange(info.copy(name = it.take(40))) }, "Team name", Modifier.fillMaxWidth())
    Spacer(Modifier.padding(4.dp))
    WttTextField(
        info.shortName, { onChange(info.copy(shortName = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(5))) },
        "Short name (max 5)", Modifier.fillMaxWidth(), caps = true,
    )
    SectionLabel("Colour")
    ColorPicker(info.colorArgb) { onChange(info.copy(colorArgb = it)) }
}

/** Validation message for a roster, or null when valid. */
fun rosterProblem(players: List<SavedPlayer>): String? {
    for (p in players) {
        val n = p.number.trim()
        if (n.isEmpty() || n.length > 2 || !n.all { it.isDigit() }) return "Jersey numbers must be 0–99 or 00"
    }
    val dup = players.groupBy { it.number.trim() }.filter { it.value.size > 1 }.keys.firstOrNull()
    return dup?.let { "#$it is used twice" }
}

/** Editable roster list (jersey + name rows). */
@Composable
fun RosterEditor(players: List<SavedPlayer>, onChange: (List<SavedPlayer>) -> Unit) {
    Column {
        players.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                WttTextField(
                    p.number, { v -> onChange(players.toMutableList().also { it[i] = p.copy(number = v.filter { c -> c.isDigit() }.take(2)) }) },
                    "#", Modifier.width(76.dp), number = true,
                )
                Spacer(Modifier.width(8.dp))
                WttTextField(
                    p.name, { v -> onChange(players.toMutableList().also { it[i] = p.copy(name = v.take(40)) }) },
                    "Name", Modifier.weight(1f),
                )
                WttIconButton(WttIcons.Trash, "Remove #${p.number}", { onChange(players.filterIndexed { j, _ -> j != i }) }, tint = Wtt.Muted)
            }
        }
        rosterProblem(players)?.let {
            Text(it, color = Wtt.Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
        }
        WttButton(
            "Add player", {
                val used = players.mapNotNull { it.number.toIntOrNull() }.toSet()
                val next = (0..99).firstOrNull { it !in used && it > (players.mapNotNull { p -> p.number.toIntOrNull() }.maxOrNull() ?: -1) }
                    ?: (0..99).firstOrNull { it !in used } ?: return@WttButton
                onChange(players + SavedPlayer(UUID.randomUUID().toString(), next.toString(), ""))
            },
            kind = ButtonKind.GHOST, icon = WttIcons.Plus, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
