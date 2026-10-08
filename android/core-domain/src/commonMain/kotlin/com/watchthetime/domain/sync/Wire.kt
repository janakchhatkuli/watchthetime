package com.watchthetime.domain.sync

import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.event.GameEvent
import com.watchthetime.domain.settings.AppSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON configuration for storage and the watch↔phone wire format. */
val WireJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // Not "type": Foul has a `type` property and would clash with the discriminator.
    classDiscriminator = "@k"
    explicitNulls = false
}

/** Data Layer paths. Same on both apps (same applicationId is required by the Data Layer). */
object SyncPaths {
    /** MessageClient phone → watch: [CommandEnvelope]. */
    const val COMMAND = "/wtt/command"
    /** MessageClient watch → phone: [EventBatch] (low-latency live deltas). */
    const val EVENTS = "/wtt/events"
    /** MessageClient watch → phone: [Heartbeat] every few seconds while a game is live. */
    const val HEARTBEAT = "/wtt/heartbeat"
    /** MessageClient phone → watch: [CommandResult]-style ack back to phone. */
    const val COMMAND_RESULT = "/wtt/command-result"
    /** MessageClient either way: ask the other side to republish a game log. */
    const val REQUEST_LOG = "/wtt/request-log"

    /** DataClient (persisted, delivered after reconnect): full log asset per game. */
    const val LOG_PREFIX = "/wtt/log/"
    /** DataClient: settings (last-writer-wins). */
    const val SETTINGS = "/wtt/settings"
    /** DataClient: game-index snapshot (titles/scores), lets either side list games. */
    const val INDEX = "/wtt/index"

    const val ASSET_KEY = "events"
    const val JSON_KEY = "json"

    /** Capability advertised by both apps (res/values/wear.xml). */
    const val CAPABILITY_WATCH = "wtt_watch"
    const val CAPABILITY_PHONE = "wtt_phone"

    fun logPath(gameId: String) = LOG_PREFIX + gameId
}

@Serializable
data class CommandEnvelope(val gameId: String, val requestId: String, val command: Command)

@Serializable
data class CommandResult(val requestId: String, val ok: Boolean, val error: String? = null)

@Serializable
data class EventBatch(val gameId: String, val events: List<GameEvent>)

@Serializable
data class Heartbeat(val gameId: String, val wallMs: Long, val running: Boolean)

@Serializable
data class LogRequest(val gameId: String)

@Serializable
data class GameIndexEntry(
    val gameId: String,
    val title: String,
    val createdWallMs: Long,
    val updatedWallMs: Long,
    val homeName: String,
    val awayName: String,
    val homeScore: Int,
    val awayScore: Int,
    val status: String,
)

@Serializable
data class GameIndex(val games: List<GameIndexEntry>)

@Serializable
@SerialName("settings_doc")
data class SettingsDoc(val settings: AppSettings)
