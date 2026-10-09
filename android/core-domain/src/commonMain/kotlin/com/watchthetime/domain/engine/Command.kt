package com.watchthetime.domain.engine

import com.watchthetime.domain.event.EventPayload
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.rules.ClockPolicy
import com.watchthetime.domain.rules.Rules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User intents. Commands are serialisable so the phone can send them to the watch during a
 * live game; the watch (single writer) turns them into events.
 */
@Serializable
sealed interface Command {
    // Clock
    @Serializable @SerialName("toggle_clock") data object ToggleClock : Command
    @Serializable @SerialName("start_clock") data object StartClock : Command
    @Serializable @SerialName("stop_clock") data object StopClock : Command
    @Serializable @SerialName("adjust_clock") data class AdjustClock(val deltaMs: Long) : Command
    @Serializable @SerialName("set_clock") data class SetClock(val remainingMs: Long) : Command

    // Shot clock
    @Serializable @SerialName("shot_reset") data class ResetShotClock(val short: Boolean = false) : Command
    @Serializable @SerialName("shot_hold") data object ToggleShotHold : Command
    @Serializable @SerialName("shot_adjust") data class AdjustShotClock(val deltaMs: Long) : Command

    // Periods
    @Serializable @SerialName("next_period") data object NextPeriod : Command
    @Serializable @SerialName("prev_period") data object PreviousPeriod : Command
    @Serializable @SerialName("end_period") data object EndPeriod : Command
    @Serializable @SerialName("finalize") data object FinalizeGame : Command
    @Serializable @SerialName("reopen") data object ReopenGame : Command

    // Score / fouls / timeouts
    @Serializable @SerialName("score") data class AddScore(val side: TeamSide, val points: Int, val playerId: String? = null) : Command
    @Serializable @SerialName("foul") data class AddFoul(val side: TeamSide, val playerId: String?, val type: FoulType = FoulType.PERSONAL) : Command
    @Serializable @SerialName("timeout") data class StartTimeout(val side: TeamSide) : Command
    @Serializable @SerialName("timeout_end") data object EndTimeout : Command
    @Serializable @SerialName("timeout_adjust") data class AdjustTimeouts(val side: TeamSide, val delta: Int) : Command

    // Setup (editable mid-game)
    @Serializable @SerialName("team") data class UpdateTeam(val side: TeamSide, val info: TeamInfo) : Command
    @Serializable @SerialName("player_upsert") data class UpsertPlayer(val player: PlayerInfo) : Command
    @Serializable @SerialName("player_remove") data class RemovePlayer(val playerId: String) : Command
    @Serializable @SerialName("rules") data class UpdateRules(val rules: Rules) : Command
    @Serializable @SerialName("clock_policy") data class SetClockPolicy(val policy: ClockPolicy) : Command

    // Event log editing
    @Serializable @SerialName("edit_event")
    data class EditEvent(
        val eventId: String,
        val payload: EventPayload,
        val period: Int? = null,
        val gameClockMs: Long? = null,
    ) : Command

    @Serializable @SerialName("assign_player") data class AssignPlayer(val eventId: String, val playerId: String?) : Command
    @Serializable @SerialName("delete_event") data class DeleteEvent(val eventId: String) : Command
    @Serializable @SerialName("restore_event") data class RestoreEvent(val eventId: String) : Command

    /** Adds an event after the fact (phone full edit), at an explicit period / clock time. */
    @Serializable @SerialName("insert_event")
    data class InsertEvent(val payload: EventPayload, val period: Int, val gameClockMs: Long) : Command

    @Serializable @SerialName("undo") data object Undo : Command
    @Serializable @SerialName("redo") data object Redo : Command
}

/** Commands that the "safe mode" setting guards with a confirmation step. */
val Command.isDestructive: Boolean
    get() = when (this) {
        Command.Undo, Command.Redo, Command.PreviousPeriod, Command.EndPeriod, Command.FinalizeGame,
        is Command.DeleteEvent, is Command.RemovePlayer, is Command.SetClock -> true
        else -> false
    }
