package com.watchthetime.domain.engine

import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.state.GameState
import com.watchthetime.domain.state.PenaltyLevel
import kotlinx.serialization.Serializable

/**
 * Every feedback-worthy moment. Each one maps to a distinct sound, a distinct vibration
 * pattern AND a visual banner (see core-feedback CueCatalog).
 */
@Serializable
enum class CueType(val label: String, val priority: Int) {
    CLOCK_START("Clock start", 2),
    CLOCK_STOP("Clock stop", 2),
    CLOCK_ADJUST("Clock adjusted", 1),
    LAST_MINUTE("Last minute", 3),
    PERIOD_END("Period end buzzer", 5),
    GAME_FINAL("Final buzzer", 5),
    PERIOD_ADVANCE("Next period", 2),
    SHOT_CLOCK_EXPIRED("Shot clock violation", 4),
    SHOT_CLOCK_RESET("Shot clock reset", 1),
    SCORE_1("Score +1", 2),
    SCORE_2("Score +2", 2),
    SCORE_3("Score +3", 2),
    FOUL("Foul", 2),
    FOUL_FLAG("Technical / flagrant / unsportsmanlike", 3),
    FOUL_WARNING("Foul trouble", 3),
    FOUL_OUT("Foul-out / ejection", 5),
    BONUS("Team in bonus", 4),
    TIMEOUT_START("Timeout start", 3),
    TIMEOUT_WARNING("Timeout ending soon", 3),
    TIMEOUT_END("Timeout end", 4),
    INTERVAL_END("Interval over", 4),
    SUBSTITUTION("Substitution", 2),
    FREE_THROWS("Free throws", 2),
    UNDO("Undo", 1),
    REDO("Redo", 1),
    EDIT_SAVED("Edit saved", 1),
    MODE_CHANGED("Clock mode changed", 3),
    ERROR("Error", 3),
}

@Serializable
data class Cue(
    val type: CueType,
    val side: TeamSide? = null,
    val playerId: String? = null,
    /** Short visual text paired with the sound (accessibility: never sound-only). */
    val text: String = type.label,
)

object CueDiff {
    /** Alerts caused purely by a change of totals (foul-outs, foul trouble, bonus). */
    fun between(before: GameState, after: GameState): List<Cue> {
        val cues = mutableListOf<Cue>()
        val rules = after.rules
        for ((id, p) in after.players) {
            val old = before.players[id]
            val wasOut = old?.disqualified(before.rules) ?: false
            val wasTrouble = old?.inFoulTrouble(before.rules) ?: false
            if (p.disqualified(rules) && !wasOut) {
                val reason = if (p.countedFouls(rules) >= rules.playerFoulLimit) "FOULED OUT" else "EJECTED"
                cues += Cue(CueType.FOUL_OUT, p.info.side, id, "#${p.info.number} $reason")
            } else if (p.inFoulTrouble(rules) && !wasTrouble && !wasOut) {
                cues += Cue(CueType.FOUL_WARNING, p.info.side, id, "#${p.info.number} ${p.countedFouls(rules)} FOULS")
            }
        }
        if (before.period == after.period) {
            for (side in TeamSide.entries) {
                val was = before.penalty(side)
                val now = after.penalty(side)
                if (now > was && now != PenaltyLevel.NONE) {
                    val shooter = after.team(side.other).info.shortName
                    val txt = if (now == PenaltyLevel.DOUBLE_BONUS) "$shooter DOUBLE BONUS" else "$shooter IN BONUS"
                    cues += Cue(CueType.BONUS, side.other, null, txt)
                }
            }
        }
        return cues
    }

    fun forFoul(type: FoulType, side: TeamSide, text: String) =
        Cue(if (type == FoulType.PERSONAL) CueType.FOUL else CueType.FOUL_FLAG, side, null, text)

    fun forScore(points: Int, side: TeamSide, text: String) = Cue(
        when (points) { 1 -> CueType.SCORE_1; 3 -> CueType.SCORE_3; else -> CueType.SCORE_2 }, side, null, text,
    )
}
