package com.ollie.iptv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class XtreamClient(private val base: String, private val user: String, private val pass: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val root = base.trim().trimEnd('/')

    fun fetch(action: String): JSONArray {
        val url = "$root/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=${enc(action)}"
        val request = Request.Builder().url(url).header("User-Agent", "OllieTV/0.2").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Provider returned HTTP ${response.code}")
            return JSONArray(response.body?.string().orEmpty())
        }
    }

    fun fetchLive(): List<StreamEntity> {
        val categories = runCatching { fetch("get_live_categories") }.getOrNull()
        val names = categoryMap(categories)
        return fetch("get_live_streams").toStreams("LIVE", "live", names)
    }
    fun fetchVod(): List<StreamEntity> {
        val categories = runCatching { fetch("get_vod_categories") }.getOrNull()
        return fetch("get_vod_streams").toStreams("MOVIE", "movie", categoryMap(categories))
    }
    fun fetchSeries(): List<StreamEntity> {
        val categories = runCatching { fetch("get_series_categories") }.getOrNull()
        return fetch("get_series").toStreams("SERIES", "series", categoryMap(categories))
    }

    private fun categoryMap(array: JSONArray?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (array != null) for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            map[o.optString("category_id")] = o.optString("category_name", "Other")
        }
        return map
    }

    private fun JSONArray.toStreams(kind: String, path: String, cats: Map<String, String>): List<StreamEntity> {
        val result = mutableListOf<StreamEntity>()
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val streamId = o.optInt("stream_id", o.optInt("series_id", -1))
            val title = o.optString("name", o.optString("title", "Untitled"))
            if (streamId < 0) continue
            val ext = o.optString("container_extension", "ts").ifBlank { "ts" }
            val url = when (kind) {
                "LIVE" -> "$root/live/${enc(user)}/${enc(pass)}/$streamId.$ext"
                "MOVIE" -> "$root/movie/${enc(user)}/${enc(pass)}/$streamId.$ext"
                else -> "$root/series/${enc(user)}/${enc(pass)}/$streamId.$ext"
            }
            result += StreamEntity(
                id = M3uParser.id("$kind|$streamId|$root"),
                playlistId = 0L,
                name = title,
                url = url,
                category = cats[o.optString("category_id")] ?: "Other",
                logo = o.optString("stream_icon", o.optString("cover", "")),
                tvgId = o.optString("epg_channel_id", ""),
                kind = kind,
                xtreamId = streamId,
                catchup = o.optInt("tv_archive", 0) == 1,
                catchupDays = o.optInt("tv_archive_duration", 0)
            )
        }
        return result
    }


    fun fetchSeriesEpisodes(seriesId: Int): List<StreamEntity> {
        val url = "$root/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_series_info&series_id=$seriesId"
        val request = Request.Builder().url(url).header("User-Agent", "OllieTV/0.2").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Provider returned HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val episodes = json.optJSONObject("episodes") ?: return emptyList()
            val result = mutableListOf<StreamEntity>()
            val seasons = episodes.keys()
            while (seasons.hasNext()) {
                val seasonKey = seasons.next()
                val list = episodes.optJSONArray(seasonKey) ?: continue
                for (i in 0 until list.length()) {
                    val episode = list.optJSONObject(i) ?: continue
                    val episodeId = episode.optInt("id", -1)
                    if (episodeId < 0) continue
                    val ext = episode.optString("container_extension", "mp4").ifBlank { "mp4" }
                    val title = episode.optString("title", "Episode ${i + 1}")
                    result += StreamEntity(
                        id = M3uParser.id("EPISODE|$seriesId|$episodeId|$root"),
                        playlistId = 0L,
                        name = title,
                        url = "$root/series/${enc(user)}/${enc(pass)}/$episodeId.$ext",
                        category = "Season $seasonKey",
                        logo = "",
                        kind = "EPISODE",
                        xtreamId = episodeId
                    )
                }
            }
            return result
        }
    }

    fun catchupUrl(streamId: Int, start: String, durationMinutes: Int): String =
        "$root/timeshift.php?username=${enc(user)}&password=${enc(pass)}&stream=$streamId&start=${enc(start)}&duration=$durationMinutes"

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
