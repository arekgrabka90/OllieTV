package com.ollie.iptv.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OllieDatabaseTest {
    @Test fun daoStoresAndQueriesPlaylistStreamsAndRecordings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, OllieDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val playlistId = database.dao().insertPlaylistWithStreams(
                PlaylistEntity(name = "Test", type = "M3U"),
                listOf(StreamEntity(
                    id = "stream-1", playlistId = 0L, name = "News", url = "https://example.test/live.ts"
                ))
            )
            database.dao().insertRecording(RecordingEntity(
                title = "News", streamUrl = "https://example.test/live.ts", localPath = "", status = "SCHEDULED"
            ))

            assertEquals(1, database.dao().allStreams().first().size)
            assertEquals("News", database.dao().allStreams().first().single().name)
            assertEquals(1, database.dao().scheduledRecordings().first().size)

            database.dao().deletePlaylistAndStreams(playlistId)
            assertEquals(0, database.dao().allStreams().first().size)
            assertEquals(null, database.dao().playlist(playlistId))
        } finally {
            database.close()
        }
    }

    @Test fun migratesVersionOneDatabaseToVersionTwoWithoutLosingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ollie-migration-test.db"
        context.deleteDatabase(name)
        val file: File = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            old.execSQL("""CREATE TABLE playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL,
                sourceUrl TEXT NOT NULL, serverUrl TEXT NOT NULL, username TEXT NOT NULL,
                password TEXT NOT NULL, epgUrl TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
            old.execSQL("""CREATE TABLE streams (
                id TEXT NOT NULL PRIMARY KEY, playlistId INTEGER NOT NULL, name TEXT NOT NULL,
                url TEXT NOT NULL, category TEXT NOT NULL, logo TEXT NOT NULL, tvgId TEXT NOT NULL,
                kind TEXT NOT NULL, xtreamId INTEGER NOT NULL, isFavorite INTEGER NOT NULL,
                catchup INTEGER NOT NULL, catchupDays INTEGER NOT NULL)""")
            old.execSQL("CREATE INDEX index_streams_playlistId ON streams(playlistId)")
            old.execSQL("CREATE INDEX index_streams_tvgId ON streams(tvgId)")
            old.execSQL("CREATE INDEX index_streams_kind ON streams(kind)")
            old.execSQL("""CREATE TABLE programmes (
                id TEXT NOT NULL PRIMARY KEY, channelId TEXT NOT NULL, title TEXT NOT NULL,
                description TEXT NOT NULL, startUtc INTEGER NOT NULL, endUtc INTEGER NOT NULL)""")
            old.execSQL("CREATE INDEX index_programmes_channelId ON programmes(channelId)")
            old.execSQL("CREATE INDEX index_programmes_startUtc ON programmes(startUtc)")
            old.execSQL("""CREATE TABLE recordings (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                streamUrl TEXT NOT NULL, localPath TEXT NOT NULL, status TEXT NOT NULL,
                startedAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL, error TEXT NOT NULL)""")
            old.execSQL("INSERT INTO playlists (name,type,sourceUrl,serverUrl,username,password,epgUrl,createdAt) VALUES ('Legacy','M3U','','','','','',123)")
            old.execSQL("PRAGMA user_version = 1")
        } finally {
            old.close()
        }

        val migrated = Room.databaseBuilder(context, OllieDatabase::class.java, name)
            .addMigrations(OllieDatabase.MIGRATION_1_2)
            .allowMainThreadQueries().build()
        try {
            val playlist = runBlocking { migrated.dao().playlist(1L) }
            assertNotNull(playlist)
            assertEquals("Legacy", playlist?.name)
            assertEquals(true, playlist?.enabled)
            assertEquals(0, playlist?.sortOrder)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
