package com.ollie.iptv.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

/**
 * Periodic safety-net refresh. The worker runs hourly and respects each playlist's
 * configured refreshIntervalHours. File-imported M3U playlists require manual re-import.
 */
class PlaylistRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = OllieDatabase.get(applicationContext).dao()
        val repository = PlaylistRepository(dao)
        val now = System.currentTimeMillis()
        val sources = dao.playlists().first().filter { it.enabled }
        var failures = 0
        val attemptedEpgUrls = mutableSetOf<String>()
        sources.forEach { source ->
            val interval = source.refreshIntervalHours.coerceIn(1, 168) * 60L * 60L * 1000L
            val due = source.lastRefreshAt == 0L || now - source.lastRefreshAt >= interval
            if (!due) return@forEach
            var sourceFailed = false
            if (!(source.type == "M3U" && source.sourceUrl == "file")) {
                runCatching { repository.refreshPlaylist(source.id) }
                    .onFailure { failures++; sourceFailed = true }
            }
            if (source.epgUrl.isNotBlank() && attemptedEpgUrls.add(source.epgUrl)) {
                val epgConfig = dao.epgSourceByUrl(source.epgUrl)
                if (epgConfig?.enabled != false) {
                    runCatching { repository.importEpg(source.epgUrl) }
                        .onFailure { failures++; sourceFailed = true }
                }
            }
            if (!sourceFailed) {
                runCatching { dao.updatePlaylist(source.copy(lastRefreshAt = now)) }
                    .onFailure { failures++ }
            }
        }
        dao.epgSources().first().filter { it.enabled }.forEach { epgSource ->
            if (attemptedEpgUrls.add(epgSource.url)) {
                runCatching { repository.importEpg(epgSource.url) }
                    .onFailure { failures++ }
            }
        }
        return if (failures > 0) Result.retry() else Result.success()
    }
}
