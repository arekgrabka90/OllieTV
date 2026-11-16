package com.ollie.iptv.recording

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Records clear MPEG-TS HLS media playlists by fetching each segment once.
 * Encrypted HLS, DRM, and fMP4/CMAF maps are rejected explicitly.
 */
object HlsRecorder {
    data class Segment(val uri: URL, val durationSeconds: Double)
    data class Playlist(val segments: List<Segment>, val isEndList: Boolean, val targetDuration: Int)

    fun record(
        source: String,
        output: File,
        shouldContinue: () -> Boolean,
        maxDurationMillis: Long,
        onProgress: (String) -> Unit
    ) {
        val started = System.currentTimeMillis()
        val seen = linkedSetOf<String>()
        var playlistUrl = URL(source)
        FileOutputStream(output, false).use { sink ->
            while (shouldContinue() && (maxDurationMillis <= 0 || System.currentTimeMillis() - started < maxDurationMillis)) {
                var playlist = loadPlaylist(playlistUrl)
                if (playlist.segments.isEmpty() && playlistUrl == URL(source)) {
                    val variant = findFirstVariant(playlistUrl)
                    if (variant != null) {
                        playlistUrl = variant
                        playlist = loadPlaylist(playlistUrl)
                    }
                }
                if (playlist.segments.isEmpty()) error("HLS playlist has no media segments.")
                var downloaded = 0
                for (segment in playlist.segments) {
                    if (!shouldContinue()) break
                    val key = segment.uri.toExternalForm()
                    if (!seen.add(key)) continue
                    val connection = segment.uri.openConnection() as HttpURLConnection
                    connection.connectTimeout = 20000
                    connection.readTimeout = 45000
                    connection.setRequestProperty("User-Agent", "OllieTV/0.4")
                    try {
                        connection.connect()
                        if (connection.responseCode !in 200..299) error("HLS segment HTTP ${connection.responseCode}")
                        connection.inputStream.use { input -> input.copyTo(sink, 128 * 1024) }
                        sink.flush()
                        downloaded++
                    } finally {
                        connection.disconnect()
                    }
                }
                onProgress("HLS: ${seen.size} segments saved")
                if (playlist.isEndList) break
                if (downloaded == 0) Thread.sleep((playlist.targetDuration.coerceIn(2, 10) * 500L))
                else Thread.sleep((playlist.targetDuration.coerceIn(2, 10) * 350L))
            }
        }
        require(output.exists() && output.length() > 0L) { "No HLS media data was recorded." }
    }

    private fun loadPlaylist(url: URL): Playlist {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "OllieTV/0.4")
        val text = try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("HLS playlist HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        require(text.lineSequence().any { it.trim() == "#EXTM3U" }) { "URL did not return an HLS playlist." }
        require(text.lineSequence().none { it.startsWith("#EXT-X-KEY", true) && !it.contains("METHOD=NONE", true) }) {
            "Encrypted HLS is not supported by this recorder."
        }
        require(text.lineSequence().none { it.startsWith("#EXT-X-MAP", true) }) {
            "fMP4/CMAF HLS is not supported by this recorder yet; MPEG-TS HLS is supported."
        }

        val segments = mutableListOf<Segment>()
        var duration = 0.0
        var target = 6
        var awaitingVariantUri = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF", true) -> awaitingVariantUri = true
                line.startsWith("#EXT-X-TARGETDURATION:") ->
                    target = line.substringAfter(':').toIntOrNull() ?: target
                line.startsWith("#EXTINF:") ->
                    duration = line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0
                line.isNotBlank() && !line.startsWith("#") && awaitingVariantUri ->
                    awaitingVariantUri = false
                line.isNotBlank() && !line.startsWith("#") -> {
                    segments += Segment(URL(url, line), duration)
                    duration = 0.0
                }
            }
        }
        return Playlist(segments, text.contains("#EXT-X-ENDLIST"), target)
    }

    private fun findFirstVariant(url: URL): URL? {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "OllieTV/0.4")
        val text = try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
        val lines = text.lineSequence().map { it.trim() }.toList()
        val index = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
        return lines.getOrNull(index + 1)?.takeIf { it.isNotBlank() && !it.startsWith("#") }?.let { URL(url, it) }
    }
}
