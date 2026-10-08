package com.watchthetime.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One row per event *id*; holds the latest revision only (the engine is the merge authority,
 * revisions are kept inside the JSON). The payload is stored as the WireJson document so the
 * schema never has to change when a new event type is added.
 */
@Entity(tableName = "events", indices = [Index("gameId")])
data class EventEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val seq: Long,
    val rev: Int,
    val deleted: Boolean,
    val json: String,
)

/** Denormalised games index for the history list (rebuilt from state after every write). */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val gameId: String,
    val title: String,
    val createdWallMs: Long,
    val updatedWallMs: Long,
    val homeName: String,
    val awayName: String,
    val homeShort: String,
    val awayShort: String,
    val homeColor: Long,
    val awayColor: Long,
    val homeScore: Int,
    val awayScore: Int,
    /** GameStatus name. */
    val status: String,
    /** e.g. "Q3 4:12" / "FINAL". */
    val progress: String,
    val preset: String,
)

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val colorArgb: Long,
    val updatedWallMs: Long,
)

@Entity(tableName = "team_players", indices = [Index("teamId")])
data class TeamPlayerEntity(
    @PrimaryKey val id: String,
    val teamId: String,
    val number: String,
    val name: String,
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE gameId = :gameId ORDER BY seq, id")
    suspend fun forGame(gameId: String): List<EventEntity>

    @Upsert
    suspend fun upsert(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE gameId = :gameId")
    suspend fun deleteGame(gameId: String)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY updatedWallMs DESC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE gameId = :gameId")
    suspend fun get(gameId: String): GameEntity?

    @Upsert
    suspend fun upsert(game: GameEntity)

    @Query("DELETE FROM games WHERE gameId = :gameId")
    suspend fun delete(gameId: String)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY name COLLATE NOCASE")
    fun observeTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun team(id: String): TeamEntity?

    @Query("SELECT * FROM team_players WHERE teamId = :teamId")
    suspend fun players(teamId: String): List<TeamPlayerEntity>

    @Query("SELECT * FROM team_players")
    fun observeAllPlayers(): Flow<List<TeamPlayerEntity>>

    @Upsert
    suspend fun upsertTeam(team: TeamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<TeamPlayerEntity>)

    @Query("DELETE FROM team_players WHERE teamId = :teamId")
    suspend fun clearPlayers(teamId: String)

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun deleteTeam(id: String)

    @Transaction
    suspend fun replace(team: TeamEntity, players: List<TeamPlayerEntity>) {
        upsertTeam(team)
        clearPlayers(team.id)
        insertPlayers(players)
    }

    @Transaction
    suspend fun deleteWithPlayers(id: String) {
        clearPlayers(id)
        deleteTeam(id)
    }
}

@Database(
    entities = [EventEntity::class, GameEntity::class, TeamEntity::class, TeamPlayerEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WttDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun games(): GameDao
    abstract fun teams(): TeamDao
}
