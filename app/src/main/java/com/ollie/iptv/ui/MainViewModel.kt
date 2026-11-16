package com.ollie.iptv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.ollie.iptv.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = OllieDatabase.get(app).dao()
    private val repository = PlaylistRepository(dao)
    val playlists = dao.playlists()
    val scheduledRecordings = dao.scheduledRecordings()
    val allStreams = dao.allStreams()
    val live = dao.streams("LIVE")
    val movies = dao.streams("MOVIE")
    val series = dao.streams("SERIES")
    val favorites = dao.favorites()
    val recordings = dao.recordings()
    val currentProgrammes = dao.allCurrentProgrammes(System.currentTimeMillis())
    private val _selected = MutableStateFlow<StreamEntity?>(null)
    val selected: StateFlow<StreamEntity?> = _selected
    private val _programmes = MutableStateFlow<List<ProgrammeEntity>>(emptyList())
    private val _guideDayOffset = MutableStateFlow(0)
    val guideDayOffset: StateFlow<Int> = _guideDayOffset
    private val _guideProgrammes = MutableStateFlow<List<ProgrammeEntity>>(emptyList())
    val guideProgrammes: StateFlow<List<ProgrammeEntity>> = _guideProgrammes
    val programmes: StateFlow<List<ProgrammeEntity>> = _programmes
    private val _episodes = MutableStateFlow<List<StreamEntity>>(emptyList())
    val episodes: StateFlow<List<StreamEntity>> = _episodes
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun importM3uUrl(name: String, url: String) = runTask {
        val count = repository.importM3uUrl(name, url)
        "Imported $count M3U entries."
    }
    fun importM3uText(name: String, text: String) = runTask {
        val count = repository.importM3uText(name, "file", text)
        "Imported $count M3U entries."
    }
    fun importXtream(name: String, server: String, username: String, password: String) = runTask {
        val count = repository.importXtream(name, server, username, password)
        "Xtream login successful. Imported $count items."
    }
    fun importEpg(url: String) = runTask {
        val count = repository.importEpg(url)
        "Imported $count EPG programmes."
    }
    fun refreshPlaylist(id: Long) = runTask {
        val count = repository.refreshPlaylist(id)
        "Refreshed playlist: $count entries."
    }
    fun setPlaylistEnabled(item: PlaylistEntity, enabled: Boolean) = viewModelScope.launch {
        repository.updatePlaylist(item.copy(enabled = enabled))
    }
    fun updatePlaylistSettings(item: PlaylistEntity, name: String, refreshHours: Int, epgUrl: String) = viewModelScope.launch {
        repository.updatePlaylist(item.copy(name = name.trim().ifBlank { item.name }, refreshIntervalHours = refreshHours, epgUrl = epgUrl))
    }
    fun deletePlaylist(id: Long) = runTask {
        repository.deletePlaylist(id)
        "Playlist deleted."
    }
    fun reorderPlaylists(items: List<PlaylistEntity>) = viewModelScope.launch {
        items.forEachIndexed { index, item -> repository.updatePlaylist(item.copy(sortOrder = index)) }
    }
    fun createScheduledRecording(
        title: String, url: String, startAt: Long, durationMinutes: Int, onCreated: (Long) -> Unit
    ) = viewModelScope.launch {
        val id = dao.insertRecording(
            RecordingEntity(
                title = title, streamUrl = CredentialVault.encrypt(url), localPath = "", status = "SCHEDULED",
                scheduledAt = startAt, durationMinutes = durationMinutes
            )
        )
        onCreated(id)
    }
    fun deleteRecording(id: Long, path: String = "") = viewModelScope.launch {
        WorkManager.getInstance(getApplication()).cancelAllWorkByTag("ollie_recording_$id")
        if (path.isNotBlank()) runCatching { java.io.File(path).delete() }
        dao.deleteRecording(id)
    }
    fun loadGuideDay(offset: Int) = viewModelScope.launch {
        _guideDayOffset.value = offset
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, offset)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        _guideProgrammes.value = dao.programmesForRange(start, start + 24L * 60L * 60L * 1000L)
    }
    fun toggleFavorite(item: StreamEntity) = viewModelScope.launch {
        dao.setFavorite(item.id, !item.isFavorite)
    }
    fun loadSeries(item: StreamEntity) = viewModelScope.launch {
        _busy.value = true
        try {
            _episodes.value = repository.seriesEpisodes(item)
            _message.value = "Loaded ${_episodes.value.size} episodes."
        } catch (t: Throwable) { _message.value = t.message ?: "Could not load episodes." }
        finally { _busy.value = false }
    }
    fun playCatchup(item: StreamEntity, date: String, time: String, duration: Int) = viewModelScope.launch {
        _busy.value = true
        try {
            val start = "$date:$time"
            val url = repository.catchupUrl(item, start, duration)
            play(item.copy(url = url, name = "${item.name} • Catch-up"))
        } catch (t: Throwable) { _message.value = t.message ?: "Catch-up failed." }
        finally { _busy.value = false }
    }
    fun play(item: StreamEntity) {
        _selected.value = item
        viewModelScope.launch {
            _programmes.value = if (item.tvgId.isNotBlank()) {
                dao.programmes(item.tvgId, System.currentTimeMillis()).first()
            } else emptyList()
        }
    }
    fun stopPlayback() { _selected.value = null }
    fun report(text: String) { _message.value = text }
    fun clearMessage() { _message.value = null }

    private fun runTask(block: suspend () -> String) = viewModelScope.launch {
        _busy.value = true; _message.value = null
        try { _message.value = block() }
        catch (t: Throwable) { _message.value = t.message ?: "Operation failed." }
        finally { _busy.value = false }
    }
}
