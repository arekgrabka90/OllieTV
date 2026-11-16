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
    @Query("DELETE FROM favorite_group_channels WHERE streamId IN (SELECT id FROM streams WHERE playlistId = :id)")
    suspend fun deletePlaylistGroupMemberships(id: Long)
    @Query("DELETE FROM channel_locks WHERE streamId IN (SELECT id FROM streams WHERE playlistId = :id)")
    suspend fun deletePlaylistChannelLocks(id: Long)
    @Query("DELETE FROM channel_history WHERE streamId IN (SELECT id FROM streams WHERE playlistId = :id)")
    suspend fun deletePlaylistChannelHistory(id: Long)
    @Transaction
    suspend fun deletePlaylistAndStreams(id: Long) {
        deletePlaylistGroupMemberships(id)
        deletePlaylistChannelLocks(id)
        deletePlaylistChannelHistory(id)
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

    @Query("SELECT * FROM favorite_groups ORDER BY name COLLATE NOCASE")
    fun favoriteGroups(): Flow<List<FavoriteGroupEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteGroup(group: FavoriteGroupEntity): Long
    @Query("DELETE FROM favorite_group_channels WHERE groupId = :groupId")
    suspend fun clearFavoriteGroupChannels(groupId: Long)
    @Query("DELETE FROM favorite_groups WHERE id = :groupId")
    suspend fun deleteFavoriteGroupRow(groupId: Long)
    @Transaction
    suspend fun deleteFavoriteGroup(groupId: Long) {
        clearFavoriteGroupChannels(groupId)
        deleteFavoriteGroupRow(groupId)
    }
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteGroupChannel(item: FavoriteGroupChannelEntity)
    @Query("DELETE FROM favorite_group_channels WHERE groupId = :groupId AND streamId = :streamId")
    suspend fun removeFavoriteGroupChannel(groupId: Long, streamId: String)
    @Query("DELETE FROM favorite_group_channels WHERE streamId = :streamId")
    suspend fun removeStreamFromFavoriteGroups(streamId: String)
    @Query("""
        SELECT s.* FROM streams s
        INNER JOIN favorite_group_channels g ON g.streamId = s.id
        INNER JOIN playlists p ON p.id = s.playlistId
        WHERE g.groupId = :groupId AND p.enabled = 1 AND s.isFavorite = 1
        ORDER BY s.name COLLATE NOCASE
    """)
    fun favoriteGroupStreams(groupId: Long): Flow<List<StreamEntity>>
    @Query("SELECT * FROM channel_locks")
    fun channelLocks(): Flow<List<ChannelLockEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM channel_locks WHERE streamId = :streamId)")
    suspend fun isChannelLocked(streamId: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun lockChannel(item: ChannelLockEntity)
    @Query("DELETE FROM channel_locks WHERE streamId = :streamId")
    suspend fun unlockChannel(streamId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordChannelHistory(item: ChannelHistoryEntity)
    @Query("""
        SELECT s.* FROM streams s
        INNER JOIN channel_history h ON h.streamId = s.id
        INNER JOIN playlists p ON p.id = s.playlistId
        WHERE s.kind = 'LIVE' AND p.enabled = 1
        ORDER BY h.lastPlayedAt DESC LIMIT :limit
    """)
    fun recentLiveStreams(limit: Int): Flow<List<StreamEntity>>
    @Query("""
        DELETE FROM channel_history
        WHERE streamId NOT IN (
            SELECT streamId FROM channel_history ORDER BY lastPlayedAt DESC LIMIT :keep
        )
    """)
    suspend fun trimChannelHistory(keep: Int)
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
    entities = [
        PlaylistEntity::class, StreamEntity::class, ProgrammeEntity::class, RecordingEntity::class,
        FavoriteGroupEntity::class, FavoriteGroupChannelEntity::class,
        ChannelHistoryEntity::class, ChannelLockEntity::class
    ],
    version = 3,
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
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_groups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_groups_name ON favorite_groups(name)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_group_channels (
                        groupId INTEGER NOT NULL,
                        streamId TEXT NOT NULL,
                        PRIMARY KEY(groupId, streamId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_group_channels_streamId ON favorite_group_channels(streamId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS channel_history (
                        streamId TEXT NOT NULL PRIMARY KEY,
                        lastPlayedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channel_history_lastPlayedAt ON channel_history(lastPlayedAt)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS channel_locks (
                        streamId TEXT NOT NULL PRIMARY KEY,
                        lockedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        @Volatile private var INSTANCE: OllieDatabase? = null
        fun get(context: Context): OllieDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, OllieDatabase::class.java, "ollie-tv.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }
}
