package com.ollie.iptv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.ollie.iptv.data.*
import androidx.media3.exoplayer.ExoPlayer
import com.ollie.iptv.player.engine.PlaybackEngine
import com.ollie.iptv.ui.live.LiveChannelFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LiveSortMode { SOURCE, NAME_ASC, NAME_DESC, CATEGORY }

enum class ChannelSecurityAction { LOCK, UNLOCK, PLAY }

data class ChannelSecurityRequest(
    val stream: StreamEntity,
    val action: ChannelSecurityAction,
    val needsPinSetup: Boolean
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = OllieDatabase.get(app).dao()
    private val repository = PlaylistRepository(dao)
    private val pinVault = ParentalPinVault(app)
    private val playbackEngine = PlaybackEngine(app)

    private val liveSearchQuery = MutableStateFlow("")
    private val liveCategoryFilter = MutableStateFlow("All channels")
    private val liveSortMode = MutableStateFlow(LiveSortMode.SOURCE)
    val liveSearch: StateFlow<String> = liveSearchQuery.asStateFlow()
    val liveCategory: StateFlow<String> = liveCategoryFilter.asStateFlow()
    val liveSort: StateFlow<LiveSortMode> = liveSortMode.asStateFlow()
    val liveCategories = dao.streams("LIVE")
        .map { items -> listOf("All channels") + items.asSequence().map { it.category }.distinct().sortedBy { it.lowercase() }.toList() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("All channels"))
    @OptIn(FlowPreview::class)
    val filteredLive = combine(
        dao.streams("LIVE"), liveSearchQuery.debounce(180), liveCategoryFilter, liveSortMode
    ) { items, query, category, sort ->
        LiveChannelFilter.apply(items, query, category, sort)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentChannels = dao.recentLiveStreams(12)
    val favoriteGroups = dao.favoriteGroups()
    private val selectedFavoriteGroupId = MutableStateFlow<Long?>(null)
    val selectedFavoriteGroup: StateFlow<Long?> = selectedFavoriteGroupId.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedFavoriteGroupStreams = selectedFavoriteGroupId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.favoriteGroupStreams(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lockedChannelIds = dao.channelLocks()
        .map { locks -> locks.mapTo(mutableSetOf()) { it.streamId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val _securityRequest = MutableStateFlow<ChannelSecurityRequest?>(null)
    val securityRequest: StateFlow<ChannelSecurityRequest?> = _securityRequest.asStateFlow()
    private val _securityError = MutableStateFlow<String?>(null)
    val securityError: StateFlow<String?> = _securityError.asStateFlow()
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()
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

    fun setLiveSearch(value: String) { liveSearchQuery.value = value }
    fun setLiveCategory(value: String) { liveCategoryFilter.value = value }
    fun setLiveSort(value: LiveSortMode) { liveSortMode.value = value }

    fun selectFavoriteGroup(groupId: Long?) { selectedFavoriteGroupId.value = groupId }

    fun createFavoriteGroup(name: String, streamId: String? = null, onCreated: (Long) -> Unit = {}) =
        viewModelScope.launch {
            val cleanName = name.trim()
            if (cleanName.isBlank()) {
                _message.value = "Enter a group name."
                return@launch
            }
            val id = dao.insertFavoriteGroup(FavoriteGroupEntity(name = cleanName))
            if (id < 0L) {
                _message.value = "A favorite group with this name already exists."
                return@launch
            }
            if (streamId != null) {
                dao.insertFavoriteGroupChannel(FavoriteGroupChannelEntity(id, streamId))
                dao.setFavorite(streamId, true)
            }
            selectedFavoriteGroupId.value = id
            onCreated(id)
        }

    fun addStreamToFavoriteGroup(groupId: Long, streamId: String) = viewModelScope.launch {
        dao.insertFavoriteGroupChannel(FavoriteGroupChannelEntity(groupId, streamId))
        dao.setFavorite(streamId, true)
        _message.value = "Channel added to favorite group."
    }

    fun deleteFavoriteGroup(groupId: Long) = viewModelScope.launch {
        dao.deleteFavoriteGroup(groupId)
        if (selectedFavoriteGroupId.value == groupId) selectedFavoriteGroupId.value = null
    }

    fun removeStreamFromFavoriteGroup(groupId: Long, streamId: String) = viewModelScope.launch {
        dao.removeFavoriteGroupChannel(groupId, streamId)
    }

    fun requestPlay(item: StreamEntity) = viewModelScope.launch {
        if (item.kind == "LIVE" && dao.isChannelLocked(item.id)) {
            requestChannelSecurity(item, ChannelSecurityAction.PLAY)
        } else {
            play(item)
        }
    }

    fun requestChannelLockToggle(item: StreamEntity) = viewModelScope.launch {
        val action = if (dao.isChannelLocked(item.id)) ChannelSecurityAction.UNLOCK else ChannelSecurityAction.LOCK
        requestChannelSecurity(item, action)
    }

    private fun requestChannelSecurity(item: StreamEntity, action: ChannelSecurityAction) {
        _securityError.value = null
        _securityRequest.value = ChannelSecurityRequest(item, action, !pinVault.hasPin())
    }

    fun cancelChannelSecurity() {
        _securityRequest.value = null
        _securityError.value = null
    }

    fun confirmChannelSecurity(pin: String, confirmation: String) = viewModelScope.launch(Dispatchers.Default) {
        val request = _securityRequest.value ?: return@launch
        val valid = if (request.needsPinSetup) {
            if (pin != confirmation) {
                withContext(Dispatchers.Main) { _securityError.value = "PIN entries do not match." }
                return@launch
            }
            if (pin.length !in 4..8 || pin.any { !it.isDigit() }) {
                withContext(Dispatchers.Main) { _securityError.value = "PIN must contain 4 to 8 digits." }
                return@launch
            }
            runCatching { pinVault.setPin(pin) }.isSuccess
        } else {
            pinVault.verify(pin)
        }
        if (!valid) {
            withContext(Dispatchers.Main) {
                _securityError.value = if (request.needsPinSetup) "Could not save PIN." else "Incorrect PIN."
            }
            return@launch
        }
        withContext(Dispatchers.Main) {
            when (request.action) {
                ChannelSecurityAction.LOCK -> viewModelScope.launch {
                    dao.lockChannel(ChannelLockEntity(request.stream.id))
                    _message.value = "Channel locked."
                }
                ChannelSecurityAction.UNLOCK -> viewModelScope.launch {
                    dao.unlockChannel(request.stream.id)
                    _message.value = "Channel unlocked."
                }
                ChannelSecurityAction.PLAY -> play(request.stream)
            }
            cancelChannelSecurity()
        }
    }

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
        val nextDay = (calendar.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        _guideProgrammes.value = dao.programmesForRange(start, nextDay)
    }
    fun toggleFavorite(item: StreamEntity) = viewModelScope.launch {
        if (item.isFavorite) dao.removeStreamFromFavoriteGroups(item.id)
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
        _player.value = playbackEngine.play(item.url)
        _selected.value = item
        if (item.kind == "LIVE") viewModelScope.launch(Dispatchers.IO) {
            dao.recordChannelHistory(ChannelHistoryEntity(item.id, System.currentTimeMillis()))
            dao.trimChannelHistory(20)
        }
        viewModelScope.launch {
            _programmes.value = if (item.tvgId.isNotBlank()) {
                dao.programmes(item.tvgId, System.currentTimeMillis()).first()
            } else emptyList()
        }
    }
    fun stopPlayback() {
        _selected.value = null
        playbackEngine.release()
        _player.value = null
    }

    override fun onCleared() {
        playbackEngine.release()
        _player.value = null
        super.onCleared()
    }
    fun report(text: String) { _message.value = text }
    fun clearMessage() { _message.value = null }

    private fun runTask(block: suspend () -> String) = viewModelScope.launch {
        _busy.value = true; _message.value = null
        try { _message.value = block() }
        catch (t: Throwable) { _message.value = t.message ?: "Operation failed." }
        finally { _busy.value = false }
    }
}
