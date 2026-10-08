package com.watchthetime.domain.event

import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.rules.Rules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A point in time as observed by the device that recorded an event.
 *
 * [monoMs] is a monotonic clock (Android `SystemClock.elapsedRealtime()`) which is immune to
 * wall-clock changes; it is only comparable when [boot] matches. When it doesn't (reboot, or
 * a different device), elapsed time falls back to [wallMs].
 */
@Serializable
data class Stamp(
    val monoMs: Long,
    val wallMs: Long,
    /** Boot counter of the recording device. -1 = unknown (forces wall-clock arithmetic). */
    val boot: Int,
) {
    /** Milliseconds from [earlier] to this stamp, never negative. */
    fun elapsedSince(earlier: Stamp): Long {
        val d = if (boot >= 0 && boot == earlier.boot) monoMs - earlier.monoMs else wallMs - earlier.wallMs
        return if (d < 0) 0 else d
    }

    fun plus(ms: Long) = Stamp(monoMs + ms, wallMs + ms, boot)
}

/**
 * The single unit of truth. Game state is never stored, only derived by folding events.
 *
 * Editing creates a new [rev] of the same [id]; deleting is a tombstone ([deleted] = true).
 * Two devices merge by keeping the highest [rev] per [id].
 */
@Serializable
data class GameEvent(
    val id: String,
    val gameId: String,
    /** Ordering key, assigned by the device that creates the event. */
    val seq: Long,
    val rev: Int = 1,
    val deleted: Boolean = false,
    val at: Stamp,
    /** Period this event belongs to (editable — e.g. a foul logged late). */
    val period: Int,
    /** Game clock reading when the event was recorded (editable). */
    val gameClockMs: Long,
    /** Node / device that created this revision ("watch", "phone"). */
    val origin: String = "watch",
    val editedAtWallMs: Long? = null,
    val payload: EventPayload,
)

@Serializable
sealed interface EventPayload

// ---- Setup / roster -----------------------------------------------------------------------

@Serializable @SerialName("game_created")
data class GameCreated(
    val rules: Rules,
    val home: TeamInfo,
    val away: TeamInfo,
    val roster: List<PlayerInfo> = emptyList(),
    val title: String = "",
) : EventPayload

@Serializable @SerialName("rules")
data class RulesUpdated(val rules: Rules) : EventPayload

@Serializable @SerialName("team")
data class TeamUpdated(val side: TeamSide, val info: TeamInfo) : EventPayload

@Serializable @SerialName("player_upsert")
data class PlayerUpserted(val player: PlayerInfo) : EventPayload

@Serializable @SerialName("player_remove")
data class PlayerRemoved(val playerId: String) : EventPayload

// ---- Game clock ---------------------------------------------------------------------------

@Serializable @SerialName("clock_start")
data object ClockStarted : EventPayload

/** Stop. Remaining time is *computed* from the start anchor so edits upstream recompute. */
@Serializable @SerialName("clock_stop")
data class ClockStopped(val expired: Boolean = false) : EventPayload

@Serializable @SerialName("clock_adjust")
data class ClockAdjusted(val deltaMs: Long) : EventPayload

@Serializable @SerialName("clock_set")
data class ClockSet(val remainingMs: Long) : EventPayload

// ---- Periods --------------------------------------------------------------------------------

/** Moves to [period] with a fresh, stopped clock. Used for next, previous and overtime. */
@Serializable @SerialName("period_set")
data class PeriodSet(val period: Int) : EventPayload

/** Manually ends the current period early (clock to 0, stopped). */
@Serializable @SerialName("period_end")
data object PeriodEnded : EventPayload

@Serializable @SerialName("game_final")
data object GameFinalized : EventPayload

@Serializable @SerialName("game_reopen")
data object GameReopened : EventPayload

// ---- Shot clock -------------------------------------------------------------------------------

@Serializable @SerialName("shot_reset")
data class ShotClockReset(val valueMs: Long) : EventPayload

@Serializable @SerialName("shot_hold")
data class ShotClockHold(val held: Boolean) : EventPayload

@Serializable @SerialName("shot_adjust")
data class ShotClockAdjusted(val deltaMs: Long) : EventPayload

// ---- Score & fouls ------------------------------------------------------------------------------

@Serializable @SerialName("score")
data class Score(val side: TeamSide, val points: Int, val playerId: String? = null) : EventPayload

@Serializable @SerialName("foul")
data class Foul(val side: TeamSide, val playerId: String?, val type: FoulType = FoulType.PERSONAL) : EventPayload

// ---- Timeouts -----------------------------------------------------------------------------------

@Serializable @SerialName("timeout_start")
data class TimeoutStarted(val side: TeamSide, val lengthMs: Long) : EventPayload

@Serializable @SerialName("timeout_end")
data class TimeoutEnded(val side: TeamSide) : EventPayload

/** Manual correction of timeouts available in the event's segment (+1 gives one back). */
@Serializable @SerialName("timeout_adjust")
data class TimeoutsAdjusted(val side: TeamSide, val delta: Int) : EventPayload

/** Human-readable kind used for log filters and CSV export. */
val EventPayload.kind: String
    get() = when (this) {
        is GameCreated -> "GAME"
        is RulesUpdated -> "RULES"
        is TeamUpdated -> "TEAM"
        is PlayerUpserted, is PlayerRemoved -> "ROSTER"
        ClockStarted, is ClockStopped, is ClockAdjusted, is ClockSet -> "CLOCK"
        is PeriodSet, PeriodEnded, GameFinalized, GameReopened -> "PERIOD"
        is ShotClockReset, is ShotClockHold, is ShotClockAdjusted -> "SHOT"
        is Score -> "SCORE"
        is Foul -> "FOUL"
        is TimeoutStarted, is TimeoutEnded, is TimeoutsAdjusted -> "TIMEOUT"
    }
