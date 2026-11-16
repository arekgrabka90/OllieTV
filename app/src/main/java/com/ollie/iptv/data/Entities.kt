package com.ollie.iptv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val sourceUrl: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    @androidx.room.ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val lastRefreshAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "12") val refreshIntervalHours: Int = 12,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "streams", indices = [Index("playlistId"), Index("tvgId"), Index("kind")])
data class StreamEntity(
    @PrimaryKey val id: String,
    val playlistId: Long,
    val name: String,
    val url: String,
    val category: String = "All channels",
    val logo: String = "",
    val tvgId: String = "",
    val kind: String = "LIVE",
    val xtreamId: Int = -1,
    val isFavorite: Boolean = false,
    val catchup: Boolean = false,
    val catchupDays: Int = 0
)

@Entity(tableName = "programmes", indices = [Index("channelId"), Index("startUtc")])
data class ProgrammeEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String = "",
    val startUtc: Long,
    val endUtc: Long
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val streamUrl: String,
    val localPath: String,
    val status: String = "SCHEDULED",
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val scheduledAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val durationMinutes: Int = 0,
    val error: String = ""
)
