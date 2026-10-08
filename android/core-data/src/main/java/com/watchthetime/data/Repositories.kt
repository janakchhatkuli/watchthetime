package com.watchthetime.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.event.GameEvent
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.settings.AppSettings
import com.watchthetime.domain.state.GameState
import com.watchthetime.domain.state.GameStatus
import com.watchthetime.domain.sync.WireJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class GameRepository(private val db: WttDatabase) {

    fun observeGames(): Flow<List<GameEntity>> = db.games().observeAll()

    suspend fun game(gameId: String): GameEntity? = db.games().get(gameId)

    suspend fun loadEvents(gameId: String): List<GameEvent> =
        db.events().forGame(gameId).mapNotNull { row ->
            runCatching { WireJson.decodeFromString(GameEvent.serializer(), row.json) }.getOrNull()
        }

    suspend fun saveEvents(events: List<GameEvent>) {
        if (events.isEmpty()) return
        db.events().upsert(events.map {
            EventEntity(it.id, it.gameId, it.seq, it.rev, it.deleted, WireJson.encodeToString(GameEvent.serializer(), it))
        })
    }

    /** Refreshes the history row for a game from its current state. */
    suspend fun updateIndex(state: GameState, clockMs: Long, nowWallMs: Long, createdWallMs: Long? = null) {
        if (!state.created) return
        val existing = db.games().get(state.gameId)
        val home = state.team(TeamSide.HOME).info
        val away = state.team(TeamSide.AWAY).info
        val progress = when (state.status) {
            GameStatus.FINAL -> "FINAL"
            GameStatus.PREGAME -> "PREGAME"
            else -> "${ClockFormat.periodShort(state.period, state.rules)} ${ClockFormat.plain(clockMs, true)}"
        }
        db.games().upsert(
            GameEntity(
                gameId = state.gameId,
                title = state.title,
                createdWallMs = existing?.createdWallMs ?: createdWallMs ?: nowWallMs,
                updatedWallMs = nowWallMs,
                homeName = home.name, awayName = away.name,
                homeShort = home.shortName, awayShort = away.shortName,
                homeColor = home.colorArgb, awayColor = away.colorArgb,
                homeScore = state.team(TeamSide.HOME).score,
                awayScore = state.team(TeamSide.AWAY).score,
                status = state.status.name,
                progress = progress,
                preset = state.rules.preset.label,
            )
        )
    }

    suspend fun deleteGame(gameId: String) {
        db.events().deleteGame(gameId)
        db.games().delete(gameId)
    }
}

data class SavedPlayer(val id: String, val number: String, val name: String)

data class SavedTeam(
    val id: String,
    val info: TeamInfo,
    val players: List<SavedPlayer>,
) {
    /** Roster for a new game; fresh player ids so games never share identities. */
    fun rosterFor(side: TeamSide): List<PlayerInfo> =
        players.map { PlayerInfo(UUID.randomUUID().toString(), side, it.number, it.name) }
}

class TeamRepository(private val db: WttDatabase) {

    fun observeTeams(): Flow<List<SavedTeam>> =
        combine(db.teams().observeTeams(), db.teams().observeAllPlayers()) { teams, players ->
            val byTeam = players.groupBy { it.teamId }
            teams.map { t ->
                SavedTeam(
                    t.id, TeamInfo(t.name, t.shortName, t.colorArgb),
                    byTeam[t.id].orEmpty().map { SavedPlayer(it.id, it.number, it.name) }.sortedWith(RosterOrder),
                )
            }
        }

    suspend fun save(team: SavedTeam) {
        db.teams().replace(
            TeamEntity(team.id, team.info.name.trim(), team.info.shortName.trim().uppercase().take(5), team.info.colorArgb, System.currentTimeMillis()),
            team.players.map { TeamPlayerEntity(it.id, team.id, it.number.trim(), it.name.trim()) },
        )
    }

    suspend fun delete(id: String) = db.teams().deleteWithPlayers(id)

    companion object {
        val RosterOrder: Comparator<SavedPlayer> =
            compareBy({ it.number.toIntOrNull() ?: 999 }, { it.number.length }, { it.number })
    }
}

/**
 * Roster CSV import. Accepts "number,name" lines (header optional), also ';' or tab
 * separated, and "name,number" when the number is clearly in the second column.
 */
object RosterCsv {
    data class Result(val players: List<SavedPlayer>, val skipped: List<String>)

    fun parse(text: String): Result {
        val players = mutableListOf<SavedPlayer>()
        val skipped = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            val cols = line.split(',', ';', '\t').map { it.trim().trim('"').trim() }
            val a = cols.getOrNull(0).orEmpty()
            val b = cols.getOrNull(1).orEmpty()
            val (num, name) = when {
                a.removePrefix("#").isJersey() -> a.removePrefix("#") to b
                b.removePrefix("#").isJersey() -> b.removePrefix("#") to a
                else -> { skipped += line; continue }
            }
            if (!seen.add(num)) { skipped += line; continue }
            players += SavedPlayer(UUID.randomUUID().toString(), num, name)
        }
        return Result(players.sortedWith(TeamRepository.RosterOrder), skipped)
    }

    private fun String.isJersey() = isNotEmpty() && length <= 2 && all { it.isDigit() }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("app_settings_json")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        prefs[key]?.let { runCatching { WireJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val cur = prefs[key]?.let { runCatching { WireJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
            val next = transform(cur).copy(updatedAtMs = System.currentTimeMillis())
            prefs[key] = WireJson.encodeToString(AppSettings.serializer(), next)
        }
    }
}

object DataModule {
    fun database(context: Context): WttDatabase =
        Room.databaseBuilder(context.applicationContext, WttDatabase::class.java, "watchthetime.db").build()
}
