package com.ollie.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

class PlaylistRepository(private val dao: OllieDao) {
    private val http = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).build()

    suspend fun importM3uUrl(name: String, url: String): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "OllieTV/0.2").build()
        val text = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) error("Playlist server returned HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        importM3uText(name, url, text)
    }

    suspend fun importM3uText(name: String, source: String, text: String): Int {
        val parsed = M3uParser.parse(text)
        require(parsed.isNotEmpty()) { "No channels found in M3U." }
        val id = dao.insertPlaylist(PlaylistEntity(name = name, type = "M3U", sourceUrl = source))
        dao.insertStreams(parsed.map {
            StreamEntity(
                id = M3uParser.id("$id|${it.tvgId}|${it.name}|${it.url}"),
                playlistId = id, name = it.name, url = it.url,
                category = it.group.ifBlank { "Other" }, logo = it.logo,
                tvgId = it.tvgId, kind = "LIVE", catchup = it.catchup, catchupDays = it.catchupDays
            )
        })
        return parsed.size
    }

    suspend fun importXtream(name: String, server: String, username: String, password: String): Int =
        withContext(Dispatchers.IO) {
            val client = XtreamClient(server, username, password)
            val live = client.fetchLive()
            require(live.isNotEmpty()) { "Login succeeded but no live channels were returned." }
            val movies = runCatching { client.fetchVod() }.getOrDefault(emptyList())
            val series = runCatching { client.fetchSeries() }.getOrDefault(emptyList())
            val playlistId = dao.insertPlaylist(
                PlaylistEntity(name = name, type = "XTREAM", serverUrl = server.trimEnd('/'),
                    username = CredentialVault.encrypt(username), password = CredentialVault.encrypt(password))
            )
            dao.insertStreams((live + movies + series).map {
                it.copy(id = M3uParser.id("$playlistId|${it.id}"), playlistId = playlistId)
            })
            live.size + movies.size + series.size
        }


    suspend fun refreshPlaylist(id: Long): Int = withContext(Dispatchers.IO) {
        val playlist = dao.playlist(id) ?: error("Playlist not found.")
        val newStreams = when (playlist.type) {
            "M3U" -> {
                val source = playlist.sourceUrl
                require(source.isNotBlank() && source != "file") { "This playlist was imported from a file. Re-import the updated file to refresh it." }
                val request = Request.Builder().url(source).header("User-Agent", "OllieTV/0.4").build()
                val text = http.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) error("Playlist server returned HTTP ${r.code}")
                    r.body?.string().orEmpty()
                }
                M3uParser.parse(text).map {
                    StreamEntity(
                        id = M3uParser.id("$id|${it.tvgId}|${it.name}|${it.url}"),
                        playlistId = id, name = it.name, url = it.url,
                        category = it.group.ifBlank { "Other" }, logo = it.logo, tvgId = it.tvgId,
                        kind = "LIVE", catchup = it.catchup, catchupDays = it.catchupDays
                    )
                }
            }
            "XTREAM" -> {
                val client = XtreamClient(playlist.serverUrl, CredentialVault.decrypt(playlist.username), CredentialVault.decrypt(playlist.password))
                (client.fetchLive() + runCatching { client.fetchVod() }.getOrDefault(emptyList()) +
                    runCatching { client.fetchSeries() }.getOrDefault(emptyList())).map {
                    it.copy(id = M3uParser.id("$id|${it.id}"), playlistId = id)
                }
            }
            else -> error("Unsupported playlist type: ${playlist.type}")
        }
        require(newStreams.isNotEmpty()) { "Refresh returned no streams; previous data was kept." }
        val previousFavorites = dao.streamsForPlaylist(id)
            .filter { it.isFavorite }
            .associateBy { "${it.kind}|${it.tvgId}|${it.name}" }
        val merged = newStreams.map { stream ->
            val wasFavorite = previousFavorites["${stream.kind}|${stream.tvgId}|${stream.name}"]?.isFavorite == true
            stream.copy(isFavorite = wasFavorite)
        }
        dao.clearStreams(id)
        dao.insertStreams(merged)
        dao.updatePlaylist(playlist.copy(lastRefreshAt = System.currentTimeMillis()))
        merged.size
    }

    suspend fun deletePlaylist(id: Long) {
        dao.deletePlaylistStreams(id)
        dao.deletePlaylist(id)
    }

    suspend fun updatePlaylist(item: PlaylistEntity) = dao.updatePlaylist(item)

    suspend fun seriesEpisodes(series: StreamEntity): List<StreamEntity> = withContext(Dispatchers.IO) {
        val playlist = dao.playlist(series.playlistId) ?: error("Playlist credentials not found.")
        require(playlist.type == "XTREAM" && series.xtreamId >= 0) { "This series source does not expose Xtream episode details." }
        val episodes = XtreamClient(playlist.serverUrl, CredentialVault.decrypt(playlist.username), CredentialVault.decrypt(playlist.password))
            .fetchSeriesEpisodes(series.xtreamId)
        dao.insertStreams(episodes.map { it.copy(playlistId = series.playlistId) })
        episodes.map { it.copy(playlistId = series.playlistId) }
    }

    suspend fun catchupUrl(stream: StreamEntity, start: String, durationMinutes: Int): String = withContext(Dispatchers.IO) {
        val playlist = dao.playlist(stream.playlistId) ?: error("Playlist credentials not found.")
        require(playlist.type == "XTREAM" && stream.xtreamId >= 0) { "Catch-up URL is not available for this source." }
        XtreamClient(playlist.serverUrl, CredentialVault.decrypt(playlist.username), CredentialVault.decrypt(playlist.password))
            .catchupUrl(stream.xtreamId, start, durationMinutes)
    }

    suspend fun importEpg(url: String): Int = withContext(Dispatchers.IO) {
        val input = URL(url).openConnection().apply {
            connectTimeout = 20000; readTimeout = 90000
        }.getInputStream()
        input.use { val programmes = XmlTvParser.parse(it); dao.insertProgrammes(programmes); programmes.size }
    }
}
