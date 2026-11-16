package com.ollie.iptv.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface OllieDao {
    @Query("SELECT * FROM playlists ORDER BY sortOrder, createdAt")
    fun playlists(): Flow<List<PlaylistEntity>>
    @Update
    suspend fun updatePlaylist(item: PlaylistEntity)
    @Query("UPDATE playlists SET enabled = :enabled WHERE id = :id")
    suspend fun setPlaylistEnabled(id: Long, enabled: Boolean)
    @Query("DELETE FROM streams WHERE playlistId = :id")
    suspend fun deletePlaylistStreams(id: Long)
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)
    @Transaction
    suspend fun deletePlaylistAndStreams(id: Long) {
        deletePlaylistStreams(id)
        deletePlaylist(id)
    }
    @Query("SELECT * FROM playlists WHERE enabled = 1 ORDER BY sortOrder, createdAt")
    fun enabledPlaylists(): Flow<List<PlaylistEntity>>
    @Insert
    suspend fun insertPlaylist(item: PlaylistEntity): Long
    @Transaction
    suspend fun insertPlaylistWithStreams(playlist: PlaylistEntity, streams: List<StreamEntity>): Long {
        val playlistId = insertPlaylist(playlist)
        insertStreams(streams.map { stream ->
            stream.copy(id = M3uParser.id("$playlistId|${stream.id}"), playlistId = playlistId)
        })
        return playlistId
    }
    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun playlist(id: Long): PlaylistEntity?
    @Query("DELETE FROM streams WHERE playlistId = :playlistId")
    suspend fun clearStreams(playlistId: Long)
    @Transaction
    suspend fun replacePlaylistStreams(playlistId: Long, items: List<StreamEntity>) {
        clearStreams(playlistId)
        insertStreams(items)
    }
    @Query("SELECT * FROM streams WHERE playlistId = :playlistId")
    suspend fun streamsForPlaylist(playlistId: Long): List<StreamEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(items: List<StreamEntity>)
    @Query("SELECT s.* FROM streams s INNER JOIN playlists p ON p.id = s.playlistId WHERE p.enabled = 1 ORDER BY p.sortOrder, s.category, s.name")
    fun allStreams(): Flow<List<StreamEntity>>
    @Query("SELECT s.* FROM streams s INNER JOIN playlists p ON p.id = s.playlistId WHERE s.kind = :kind AND p.enabled = 1 ORDER BY p.sortOrder, s.category, s.name")
    fun streams(kind: String): Flow<List<StreamEntity>>
    @Query("SELECT s.* FROM streams s INNER JOIN playlists p ON p.id = s.playlistId WHERE s.isFavorite = 1 AND p.enabled = 1 ORDER BY s.name")
    fun favorites(): Flow<List<StreamEntity>>
    @Query("UPDATE streams SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
    @Query("SELECT * FROM streams WHERE id = :id LIMIT 1")
    suspend fun stream(id: String): StreamEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammes(items: List<ProgrammeEntity>)
    @Query("SELECT * FROM programmes WHERE channelId = :channelId AND endUtc > :now ORDER BY startUtc LIMIT 8")
    fun programmes(channelId: String, now: Long): Flow<List<ProgrammeEntity>>
    @Query("SELECT * FROM programmes WHERE endUtc > :now ORDER BY startUtc")
    fun allCurrentProgrammes(now: Long): Flow<List<ProgrammeEntity>>
    @Query("SELECT * FROM programmes WHERE startUtc < :endUtc AND endUtc > :startUtc ORDER BY startUtc")
    suspend fun programmesForRange(startUtc: Long, endUtc: Long): List<ProgrammeEntity>
    @Query("DELETE FROM programmes WHERE startUtc < :beforeUtc")
    suspend fun deleteOldProgrammes(beforeUtc: Long)
    @Insert
    suspend fun insertRecording(item: RecordingEntity): Long
    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun recordings(): Flow<List<RecordingEntity>>
    @Query("UPDATE recordings SET status = :status, finishedAt = :finishedAt, error = :error, localPath = :localPath WHERE id = :id")
    suspend fun updateRecording(id: Long, status: String, finishedAt: Long, localPath: String, error: String = "")
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecording(id: Long)
    @Query("UPDATE recordings SET status = :status, startedAt = :startedAt WHERE id = :id")
    suspend fun setRecordingStatus(id: Long, status: String, startedAt: Long)
    @Query("SELECT * FROM recordings WHERE status = 'SCHEDULED' ORDER BY scheduledAt")
    fun scheduledRecordings(): Flow<List<RecordingEntity>>
}

@Database(
    entities = [PlaylistEntity::class, StreamEntity::class, ProgrammeEntity::class, RecordingEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OllieDatabase : RoomDatabase() {
    abstract fun dao(): OllieDao
    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE playlists ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlists ADD COLUMN lastRefreshAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlists ADD COLUMN refreshIntervalHours INTEGER NOT NULL DEFAULT 12")
                db.execSQL("ALTER TABLE recordings ADD COLUMN scheduledAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recordings ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var INSTANCE: OllieDatabase? = null
        fun get(context: Context): OllieDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, OllieDatabase::class.java, "ollie-tv.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
