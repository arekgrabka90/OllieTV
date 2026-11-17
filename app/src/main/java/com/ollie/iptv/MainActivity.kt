package com.ollie.iptv

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import java.io.File
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.ollie.iptv.recording.ScheduledRecordingWorker
import androidx.work.WorkManager
import com.ollie.iptv.data.PlaylistRefreshWorker
import java.util.concurrent.TimeUnit
import com.ollie.iptv.data.*
import com.ollie.iptv.recording.RecordingService
import com.ollie.iptv.ui.MainViewModel
import com.ollie.iptv.ui.SettingsScreen
import com.ollie.iptv.ui.VodDetailsScreen
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.ollie.iptv.ui.live.LiveTvScreen
import com.ollie.iptv.ui.live.ChannelSecurityDialog
import com.ollie.iptv.ui.live.FavoriteGroupPickerDialog
import com.ollie.iptv.navigation.ScreenTab
import com.ollie.iptv.navigation.OllieNavigationItem
import com.ollie.iptv.player.ui.OlliePlayer
import com.ollie.iptv.player.ui.OllieMiniPlayer
import com.ollie.iptv.ui.home.TVHubScreen
import com.ollie.iptv.ui.home.LandscapeHomeDashboard
import com.ollie.iptv.ui.adaptive.OllieWindowClass
import com.ollie.iptv.ui.adaptive.ollieWindowClassForWidth
import com.ollie.iptv.ui.adaptive.ollieUsesTwoPaneLiveLayout
import com.ollie.iptv.ui.theme.Ink
import com.ollie.iptv.ui.theme.Panel
import com.ollie.iptv.ui.theme.Muted


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isPhoneWindow = resources.configuration.smallestScreenWidthDp < 600
        window.decorView.systemUiVisibility = if (isPhoneWindow) {
            0
        } else {
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        val refreshRequest = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ollie_playlist_auto_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            refreshRequest
        )
        setContent {
            com.ollie.iptv.ui.theme.OllieMaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    OllieApp()
                }
            }
        }
    }
}

@Composable
private fun OllieApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val live by vm.live.collectAsState(initial = emptyList())
    val filteredLive by vm.filteredLive.collectAsState()
    val liveCategories by vm.liveCategories.collectAsState()
    val liveSearch by vm.liveSearch.collectAsState()
    val liveCategory by vm.liveCategory.collectAsState()
    val liveSort by vm.liveSort.collectAsState()
    val recentChannels by vm.recentChannels.collectAsState(initial = emptyList())
    val lockedChannelIds by vm.lockedChannelIds.collectAsState()
    val favoriteGroups by vm.favoriteGroups.collectAsState(initial = emptyList())
    val selectedFavoriteGroup by vm.selectedFavoriteGroup.collectAsState()
    val selectedFavoriteGroupStreams by vm.selectedFavoriteGroupStreams.collectAsState()
    val securityRequest by vm.securityRequest.collectAsState()
    val securityError by vm.securityError.collectAsState()
    val previewProgrammes by vm.currentProgrammes.collectAsState(initial = emptyList())
    val movies by vm.movies.collectAsState(initial = emptyList())
    val series by vm.series.collectAsState(initial = emptyList())
    val favorites by vm.favorites.collectAsState(initial = emptyList())
    val recordings by vm.recordings.collectAsState(initial = emptyList())
    val playlists by vm.playlists.collectAsState(initial = emptyList())
    val epgSources by vm.epgSources.collectAsState(initial = emptyList())
    val epgChannels by vm.epgChannels.collectAsState(initial = emptyList())
    val channelMappings by vm.channelMappings.collectAsState(initial = emptyList())
    val scheduledProgrammeReminders by vm.scheduledProgrammeReminders.collectAsState(initial = emptyList())
    val guideProgrammes by vm.guideProgrammes.collectAsState()
    val guideDayOffset by vm.guideDayOffset.collectAsState()
    LaunchedEffect(Unit) { vm.loadGuideDay(0) }
    val episodes by vm.episodes.collectAsState()
    val selected by vm.selected.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    var tab by rememberSaveable { mutableStateOf(ScreenTab.HUB) }
    var moreMenuExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab == ScreenTab.GUIDE) tab = ScreenTab.LIVE }
    var playerFullscreen by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var groupTargetStreamId by rememberSaveable { mutableStateOf<String?>(null) }
    var groupPickerVisible by rememberSaveable { mutableStateOf(false) }
    var createGroupVisible by rememberSaveable { mutableStateOf(false) }
    var newGroupName by rememberSaveable { mutableStateOf("") }
    var deleteGroupTarget by remember { mutableStateOf<FavoriteGroupEntity?>(null) }
    var selectedSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSeries = series.firstOrNull { it.id == selectedSeriesId }
    var detailTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val detailTarget = (movies + series).firstOrNull { it.id == detailTargetId }
    val settings by vm.settings.collectAsState()
    val vodDetails by vm.vodDetails.collectAsState()
    val vodDetailsLoading by vm.vodDetailsLoading.collectAsState()
    val vodDetailsError by vm.vodDetailsError.collectAsState()
    var catchupTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val catchupTarget = live.firstOrNull { it.id == catchupTargetId }
    var scheduleDialog by rememberSaveable { mutableStateOf(false) }
    var pendingProgrammeReminder by remember { mutableStateOf<Triple<StreamEntity, ProgrammeEntity, Int>?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingProgrammeReminder?.let { (channel, programme, minutes) ->
            if (granted) vm.scheduleProgrammeReminder(channel, programme, minutes)
            else vm.report("Notification permission was denied; the reminder was not scheduled.")
        }
        pendingProgrammeReminder = null
    }
    val scheduleProgrammeReminder: (StreamEntity, ProgrammeEntity, Int) -> Unit = { channel, programme, minutes ->
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            pendingProgrammeReminder = Triple(channel, programme, minutes)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.scheduleProgrammeReminder(channel, programme, minutes)
    }
    val player by vm.player.collectAsState()
    val handleChannelClick: (StreamEntity) -> Unit = { channel ->
        if (selected?.id == channel.id && player != null) playerFullscreen = true
        else { playerFullscreen = false; vm.requestPlay(channel) }
    }

    BackHandler(
        enabled = playerFullscreen || (selected != null && selected?.kind != "LIVE") || dialog.isNotBlank() || catchupTargetId != null ||
            scheduleDialog || selectedSeriesId != null || detailTargetId != null || groupPickerVisible || createGroupVisible ||
            deleteGroupTarget != null || securityRequest != null
    ) {
        when {
            securityRequest != null -> vm.cancelChannelSecurity()
            playerFullscreen && selected?.kind == "LIVE" -> playerFullscreen = false
            deleteGroupTarget != null -> deleteGroupTarget = null
            groupPickerVisible -> groupPickerVisible = false
            createGroupVisible -> createGroupVisible = false
            catchupTargetId != null -> catchupTargetId = null
            scheduleDialog -> scheduleDialog = false
            detailTargetId != null -> { detailTargetId = null; vm.clearVodDetails() }
            dialog.isNotBlank() -> dialog = ""
            selected != null -> vm.stopPlayback()
            selectedSeriesId != null -> selectedSeriesId = null
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot open selected playlist.")
            }.onSuccess { vm.importM3uText("Imported M3U", it) }
                .onFailure { vm.report(it.message ?: "File import failed.") }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
            primary = Color(0xFFFF8A32), onPrimary = Color(0xFF1B1420),
            primaryContainer = Color(0xFF49301F), onPrimaryContainer = Color(0xFFF5F3FA),
            background = Ink, surface = Panel, surfaceVariant = Color(0xFF292638),
            onBackground = Color(0xFFF5F3FA), onSurface = Color(0xFFF5F3FA),
            onSurfaceVariant = Color(0xFFA5A1B2), secondary = Color(0xFFBEB6CC),
            outline = Color(0xFF393343), error = Color(0xFFF04452)
    )) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (selected != null && player != null && (playerFullscreen || selected!!.kind != "LIVE")) {
                OlliePlayer(
                    player = player!!,
                    stream = selected!!,
                    programmes = vm.programmes.collectAsState().value,
                    allProgrammes = previewProgrammes,
                    guideProgrammes = guideProgrammes,
                    guideDayOffset = guideDayOffset,
                    onGuideDayChange = vm::loadGuideDay,
                    channelMappings = channelMappings,
                    onBack = { if (selected?.kind == "LIVE") playerFullscreen = false else vm.stopPlayback() },
                    channels = live,
                    onZap = vm::requestPlay,
                    onCatchup = { catchupTargetId = selected?.id },
                    onSchedule = { scheduleDialog = true },
                    onRecord = {
                        val intent = Intent(context, RecordingService::class.java).apply {
                            putExtra(RecordingService.EXTRA_URL, selected!!.url)
                            putExtra(RecordingService.EXTRA_TITLE, selected!!.name)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                        else context.startService(intent)
                        vm.report("Recording started. Clear MPEG-TS HLS and direct HTTP streams are supported; encrypted/fMP4 HLS is not.")
                    },
                    onStopRecord = {
                        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
                        vm.report("Stop requested for the active recording.")
                    },
                    hudTimeoutSeconds = settings.hudTimeoutSeconds
                )
                if (scheduleDialog) {
                    ScheduleRecordingDialog(
                        onDismiss = { scheduleDialog = false },
                        onSchedule = { startAt, duration ->
                            scheduleDialog = false
                            vm.createScheduledRecording(selected!!.name, selected!!.url, startAt, duration) { recordingId ->
                                val request = OneTimeWorkRequestBuilder<ScheduledRecordingWorker>()
                                    .setInitialDelay((startAt - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                    .setInputData(workDataOf(
                                        ScheduledRecordingWorker.KEY_ID to recordingId,
                                        ScheduledRecordingWorker.KEY_TITLE to selected!!.name,
                                        ScheduledRecordingWorker.KEY_URL to CredentialVault.encrypt(selected!!.url),
                                        ScheduledRecordingWorker.KEY_DURATION to duration
                                    ))
                                    .addTag("ollie_recording_$recordingId")
                                    .build()
                                WorkManager.getInstance(context).enqueueUniqueWork(
                                    "ollie_recording_$recordingId",
                                    ExistingWorkPolicy.REPLACE,
                                    request
                                )
                                vm.report("Recording scheduled.")
                            }
                        }
                    )
                }
                catchupTarget?.let { target ->
                    CatchupDialog(
                        onDismiss = { catchupTargetId = null },
                        onSubmit = { date, time, duration ->
                            catchupTargetId = null
                            vm.playCatchup(target, date, time, duration)
                        }
                    )
                }
            } else if (detailTargetId != null && detailTarget != null) {
                VodDetailsScreen(stream=detailTarget,details=vodDetails,loading=vodDetailsLoading,error=vodDetailsError,
                    compactRows=settings.compactRows,onBack={detailTargetId=null;vm.clearVodDetails()},
                    onPlay={vm.requestPlay(detailTarget)},onPlayEpisode=vm::requestPlay)
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val availableWidth = maxWidth
                    val compact = availableWidth < 680.dp
                    val contentWidthDp = (availableWidth - 224.dp).value.toInt().coerceAtLeast(0)
                    val useTwoPaneLiveLayout = ollieUsesTwoPaneLiveLayout(contentWidthDp)
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Column(Modifier.width(194.dp).fillMaxHeight()) {
                            Text("OLLIE", fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.White)
                            Text("TELEVISION", color = Muted, fontSize = 9.sp, letterSpacing = 3.sp)
                            Spacer(Modifier.height(22.dp))
                            ScreenTab.values().filterNot { it == ScreenTab.GUIDE }.forEach { item ->
                                OllieNavigationItem(item, tab == item, compact = false) { tab = item }
                            }
                            Spacer(Modifier.weight(1f))
                            Text("OLLIE TV  •  0.5.0", color = Muted, fontSize = 10.sp)
                        }
                        Column(Modifier.fillMaxSize().padding(start = 20.dp)) {
                        if (tab != ScreenTab.HUB) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tab.label, fontSize = if (compact) 22.sp else 27.sp, fontWeight = FontWeight.Bold)
                                Text(when(tab) {
                                    ScreenTab.HUB -> "Your live television hub"
                                    ScreenTab.LIVE -> "${live.size} channels"
                                    ScreenTab.MOVIES -> "${movies.size} movies"
                                    ScreenTab.SERIES -> if (selectedSeries == null) "${series.size} series" else "Episodes • ${selectedSeries?.name}"
                                    ScreenTab.FAVORITES -> "${favorites.size} favorites"
                                    ScreenTab.GUIDE -> "Programme schedule"
                                    ScreenTab.RECORDINGS -> "${recordings.size} recordings"
                                    ScreenTab.SETTINGS -> "App preferences"
                                }, color = Muted, fontSize = 13.sp)
                            }
                        }

                        } else {
                            // The HUB dashboard provides its own title and summary.
                        }
                        Spacer(Modifier.height(14.dp))
                        if (tab == ScreenTab.MOVIES || tab == ScreenTab.SERIES || tab == ScreenTab.FAVORITES) {
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 58.dp),
                                placeholder = {
                                    Text(
                                        when (tab) {
                                            ScreenTab.MOVIES -> "Search movies"
                                            ScreenTab.SERIES -> "Search series"
                                            else -> "Search favorites"
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            ScreenTab.HUB -> LandscapeHomeDashboard(
                                channelCount = live.size, playlistCount = playlists.size, movieCount = movies.size,
                                seriesCount = series.size, favoriteCount = favorites.size, recordingCount = recordings.size,
                                recentChannels = recentChannels, onNavigate = { tab = it }, onPlayChannel = handleChannelClick
                            )
                            ScreenTab.LIVE -> TVHubScreen(
                                channels = filteredLive,
                                allChannels = live,
                                recentChannels = recentChannels,
                                favorites = favorites.filter { it.kind == "LIVE" },
                                playlists = playlists,
                                selectedCategory = liveCategory,
                                selectedChannel = selected?.takeIf { it.kind == "LIVE" },
                                search = liveSearch,
                                sortMode = liveSort,
                                programmes = previewProgrammes,
                                channelMappings = channelMappings,
                                lockedChannelIds = lockedChannelIds,
                                expandedLayout = useTwoPaneLiveLayout,
                                compactLayout = compact,
                                onSearchChange = vm::setLiveSearch,
                                onCategoryChange = vm::setLiveCategory,
                                onSortChange = vm::setLiveSort,
                                onChannelClick = handleChannelClick,
                                onFavorite = vm::toggleFavorite,
                                onLockToggle = vm::requestChannelLockToggle,
                                onAddToGroup = { stream ->
                                    groupTargetStreamId = stream.id
                                    groupPickerVisible = true
                                },
                                showLogos = settings.showChannelLogos,
                                showCategory = settings.showChannelCategory,
                                showRecentChannels = settings.showRecentChannels,
                                compactRows = settings.compactRows,
                                guideContent = {
                                    GuideList(
                                        channels = live,
                                        programmes = guideProgrammes,
                                        query = liveSearch,
                                        dayOffset = guideDayOffset,
                                        onDayChange = vm::loadGuideDay,
                                        onPlay = handleChannelClick,
                                        onReminder = scheduleProgrammeReminder,
                                        epgChannels = epgChannels,
                                        onMap = vm::saveManualEpgMapping,
                                        mappings = channelMappings,
                                        reminders = scheduledProgrammeReminders,
                                        onCancelReminder = vm::cancelProgrammeReminder,
                                        compact = compact
                                    )
                                }
                            )
                            ScreenTab.GUIDE -> { tab = ScreenTab.LIVE }
                            ScreenTab.MOVIES -> StreamList(movies.filter { it.name.contains(search,true) },vm::requestPlay,vm::toggleFavorite,
                                onDetails={detailTargetId=it.id;vm.loadVodDetails(it)},showLogos=settings.showChannelLogos,compactRows=settings.compactRows)
                            ScreenTab.SERIES -> {
                                if (selectedSeries == null) {
                                    SeriesList(series.filter { it.name.contains(search,true) },onSelect={detailTargetId=it.id;vm.loadVodDetails(it)},
                                        onFavorite=vm::toggleFavorite,showLogos=settings.showChannelLogos,compactRows=settings.compactRows)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { selectedSeriesId = null }) { Icon(Icons.Default.ArrowBack, null); Text("All series") }
                                    }
                                    StreamList(episodes,vm::requestPlay,vm::toggleFavorite,showLogos=settings.showChannelLogos,compactRows=settings.compactRows)
                                }
                            }
                            ScreenTab.FAVORITES -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = selectedFavoriteGroup == null,
                                        onClick = { vm.selectFavoriteGroup(null) },
                                        label = { Text("All favorites") }
                                    )
                                    favoriteGroups.forEach { group ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilterChip(
                                                selected = selectedFavoriteGroup == group.id,
                                                onClick = { vm.selectFavoriteGroup(group.id) },
                                                label = { Text(group.name) }
                                            )
                                            IconButton(
                                                onClick = { deleteGroupTarget = group },
                                                modifier = Modifier.size(34.dp)
                                            ) { Icon(Icons.Default.DeleteOutline, "Delete ${group.name}", tint = Muted) }
                                        }
                                    }
                                    TextButton(onClick = { newGroupName = ""; createGroupVisible = true }) {
                                        Icon(Icons.Default.Add, null); Text("New group")
                                    }
                                }
                                val visibleFavorites = if (selectedFavoriteGroup == null) favorites else selectedFavoriteGroupStreams
                                StreamList(
                                    visibleFavorites.filter { it.name.contains(search, true) },
                                    onPlay = handleChannelClick,
                                    onFavorite = vm::toggleFavorite,
                                    onRemoveFromGroup = selectedFavoriteGroup?.let { groupId ->
                                        { stream -> vm.removeStreamFromFavoriteGroup(groupId, stream.id) }
                                    }
                                )
                            }
                            ScreenTab.SETTINGS -> SettingsScreen(
                                settings = settings, onShowLogos = vm::setShowChannelLogos,
                                onShowCategory = vm::setShowChannelCategory, onShowRecent = vm::setShowRecentChannels,
                                onCompactRows = vm::setCompactRows, onHudTimeout = vm::setHudTimeoutSeconds,
                                onAddPlaylist = { dialog = "add" },
                                playlistContent = {
                                    PlaylistManagerList(
                                        modifier = Modifier, playlists = playlists,
                                        onEnabled = vm::setPlaylistEnabled, onSettings = vm::updatePlaylistSettings,
                                        onRefresh = vm::refreshPlaylist, onDelete = vm::deletePlaylist,
                                        onMove = { direction ->
                                            val ordered = playlists.toMutableList()
                                            val index = ordered.indexOfFirst { it.id == direction.first }
                                            val target = index + direction.second
                                            if (index >= 0 && target in ordered.indices) {
                                                val item = ordered.removeAt(index); ordered.add(target, item); vm.reorderPlaylists(ordered)
                                            }
                                        }
                                    )
                                },
                                epgContent = {
                                    EpgSourcesPanel(sources = epgSources, onAdd = vm::addEpgSource,
                                        onRefresh = vm::refreshEpgSource, onEnabled = vm::setEpgSourceEnabled, onDelete = vm::deleteEpgSource)
                                }
                            )
                            ScreenTab.RECORDINGS -> {
                                if (recordings.isEmpty()) EmptyState("No recordings yet", "Start a recording from the player.")
                                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(recordings, key = { it.id }) { r ->
                                        Surface(
                                            onClick = {
                                                if (r.localPath.isNotBlank() && File(r.localPath).exists()) {
                                                    vm.play(StreamEntity(
                                                        id = "recording-${r.id}", playlistId = 0L, name = r.title,
                                                        url = Uri.fromFile(File(r.localPath)).toString(),
                                                        category = "Recordings", kind = "RECORDING"
                                                    ))
                                                } else vm.report("This recording has no playable local file.")
                                            },
                                            color = Panel, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.VideoFile, null, tint = Color.LightGray)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(r.title, fontWeight = FontWeight.SemiBold)
                                                    val detail = if (r.status == "SCHEDULED" && r.scheduledAt > 0L) {
                                                        "Scheduled • " + java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(r.scheduledAt))
                                                    } else "${r.status} • ${r.localPath.ifBlank { r.error }}"
                                                    Text(detail, color = Muted, fontSize = 11.sp)
                                                }
                                                Icon(Icons.Default.PlayArrow, null, tint = Muted)
                                                IconButton(onClick = { vm.deleteRecording(r.id, r.localPath) }) {
                                                    Icon(Icons.Default.DeleteOutline, "Delete recording", tint = Muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                        if (selected?.kind == "LIVE" && player != null) {
                            OllieMiniPlayer(
                                player = player!!,
                                stream = selected!!,
                                onExpand = { playerFullscreen = true },
                                onClose = { playerFullscreen = false; vm.stopPlayback() },
                                compact = compact
                            )
                        }
                        message?.let {
                            Spacer(Modifier.height(8.dp))
                            Surface(color = Color(0xFF202A34), shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(it, Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
                                    TextButton(onClick = vm::clearMessage) { Text("Dismiss") }
                                }
                            }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    }
                }
            }
        }
    }

    when (dialog) {
        "add" -> AddSourceDialog(
            onDismiss = { dialog = "" },
            onFile = { dialog = ""; filePicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")) },
            onUrl = { name, url -> dialog = ""; vm.importM3uUrl(name, url) },
            onXtream = { name, server, user, pass -> dialog = ""; vm.importXtream(name, server, user, pass) }
        )
        "epg" -> SimpleUrlDialog("Import XMLTV EPG", "XMLTV URL", onDismiss = { dialog = "" }) {
            dialog = ""; vm.importEpg(it)
        }
    }

    securityRequest?.let { request ->
        ChannelSecurityDialog(
            request = request,
            error = securityError,
            onConfirm = vm::confirmChannelSecurity,
            onDismiss = vm::cancelChannelSecurity
        )
    }

    if (groupPickerVisible) {
        FavoriteGroupPickerDialog(
            groups = favoriteGroups,
            onSelect = { group ->
                groupTargetStreamId?.let { vm.addStreamToFavoriteGroup(group.id, it) }
                groupTargetStreamId = null
                groupPickerVisible = false
            },
            onCreate = {
                groupPickerVisible = false
                newGroupName = ""
                createGroupVisible = true
            },
            onDismiss = {
                groupPickerVisible = false
                groupTargetStreamId = null
            }
        )
    }

    if (createGroupVisible) {
        AlertDialog(
            onDismissRequest = { createGroupVisible = false },
            title = { Text("Create favorite group") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = groupTargetStreamId
                    vm.createFavoriteGroup(newGroupName, target)
                    groupTargetStreamId = null
                    createGroupVisible = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    createGroupVisible = false
                    groupTargetStreamId = null
                }) { Text("Cancel") }
            }
        )
    }

    deleteGroupTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteGroupTarget = null },
            title = { Text("Delete favorite group?") },
            text = { Text("The group \"${group.name}\" and its memberships will be deleted. Channels remain in your favorites.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFavoriteGroup(group.id)
                    deleteGroupTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteGroupTarget = null }) { Text("Cancel") } }
        )
    }
}


@Composable
private fun StreamList(
    items: List<StreamEntity>,
    onPlay: (StreamEntity) -> Unit,
    onFavorite: (StreamEntity) -> Unit,
    onRemoveFromGroup: ((StreamEntity) -> Unit)? = null,
    onDetails: ((StreamEntity) -> Unit)? = null,
    showLogos: Boolean = true,
    compactRows: Boolean = false
) {
    if (items.isEmpty()) { EmptyState("Nothing here yet", "Add an M3U playlist or Xtream account."); return }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(154.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 18.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        items(items, key = { it.id }) { stream ->
            Surface(onClick = { if (onDetails != null) onDetails(stream) else onPlay(stream) }, color = Color(0xFF292638), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFF393343)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.72f).background(Color(0xFF211E2D), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        if (showLogos && stream.logo.isNotBlank()) AsyncImage(model = stream.logo, contentDescription = "${stream.name} artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(if (stream.kind == "SERIES") Icons.Default.VideoLibrary else Icons.Default.Movie, null, tint = Color(0xFFFF8A32), modifier = Modifier.size(38.dp))
                        Surface(color = Color(0xCC12111D), shape = RoundedCornerShape(9.dp), modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)) {
                            IconButton(onClick = { onFavorite(stream) }, modifier = Modifier.size(34.dp)) { Icon(if (stream.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = if (stream.isFavorite) Color(0xFFFF8A32) else Color.White) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stream.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stream.category, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (onRemoveFromGroup != null) TextButton(onClick = { onRemoveFromGroup(stream) }, contentPadding = PaddingValues(0.dp)) { Text("Remove from group", color = Color(0xFFFF8A32), fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun SeriesList(items: List<StreamEntity>, onSelect: (StreamEntity) -> Unit, onFavorite: (StreamEntity) -> Unit, showLogos: Boolean = true, compactRows: Boolean = false) {
    StreamList(items = items, onPlay = onSelect, onFavorite = onFavorite, showLogos = showLogos, compactRows = compactRows)
}

@Composable
private fun CatchupDialog(onDismiss: () -> Unit, onSubmit: (String, String, Int) -> Unit) {
    var date by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }
    var time by remember { mutableStateOf("20-00") }
    var duration by remember { mutableStateOf("60") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play catch-up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the programme start time in the provider's local time.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Start (HH-MM)") }, singleLine = true)
                OutlinedTextField(duration, { duration = it }, label = { Text("Duration in minutes") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (date.matches(Regex("""\d{4}-\d{2}-\d{2}""")) &&
                    time.matches(Regex("""\d{2}-\d{2}""")) && (duration.toIntOrNull() ?: 0) > 0) {
                    onSubmit(date, time, duration.toInt())
                }
            }) { Text("Play") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GuideList(
    channels: List<StreamEntity>,
    programmes: List<ProgrammeEntity>,
    query: String,
    dayOffset: Int,
    onDayChange: (Int) -> Unit,
    onPlay: (StreamEntity) -> Unit,
    onReminder: (StreamEntity, ProgrammeEntity, Int) -> Unit,
    epgChannels: List<EpgChannelEntity>,
    onMap: (String, String) -> Unit,
    mappings: List<ChannelMappingEntity>,
    reminders: List<ProgrammeReminderEntity>,
    onCancelReminder: (ProgrammeReminderEntity) -> Unit,
    compact: Boolean = false
) {
    var selectedProgramme by remember { mutableStateOf<ProgrammeEntity?>(null) }
    var selectedChannel by remember { mutableStateOf<StreamEntity?>(null) }
    var mappingChannel by remember { mutableStateOf<StreamEntity?>(null) }
    val dayCalendar = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
    }
    val dayLabel = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(dayCalendar.time)
    val startCalendar = (dayCalendar.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val dayStart = startCalendar.timeInMillis
    val endCalendar = (startCalendar.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
    val dayEnd = endCalendar.timeInMillis
    val dayHours = ((dayEnd - dayStart) / 3_600_000L).toInt().coerceIn(23, 25)
    val scale = 2.0f
    val timelineScrollState = rememberScrollState()
    val programmesByChannel = remember(programmes) { programmes.groupBy { it.channelId } }
    val mappingByStream = remember(mappings) { mappings.associateBy { it.streamId } }
    val visibleChannels = channels.filter { channel ->
        val epgId = mappingByStream[channel.id]?.xmltvChannelId ?: channel.tvgId
        query.isBlank() || channel.name.contains(query, true) || programmesByChannel[epgId].orEmpty().any {
            it.title.contains(query, true) || it.description.contains(query, true)
        }
    }
    if (channels.isEmpty()) { EmptyState("No channels", "Import a playlist first."); return }

    if (compact) {
        CompactGuideList(
            channels = visibleChannels,
            programmesByChannel = programmesByChannel,
            mappingByStream = mappingByStream,
            dayStart = dayStart,
            dayEnd = dayEnd,
            dayLabel = dayLabel,
            reminders = reminders,
            onDayChange = onDayChange,
            onPlay = onPlay,
            onProgrammeSelected = { channel, programme ->
                selectedProgramme = programme
                selectedChannel = channel
            },
            onMapChannel = { mappingChannel = it },
            onCancelReminder = onCancelReminder
        )
    } else {
    Column(Modifier.fillMaxSize()) {
        if (reminders.isNotEmpty()) {
            Text("UPCOMING REMINDERS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            reminders.take(3).forEach { reminder ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${reminder.programmeTitle} • ${reminder.minutesBefore} min", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { onCancelReminder(reminder) }) { Text("Cancel") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onDayChange(dayOffset - 1) }) { Icon(Icons.Default.ChevronLeft, null); Text("Previous") }
            Text(dayLabel, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onDayChange(0) }) { Text("Today") }
            OutlinedButton(onClick = { onDayChange(dayOffset + 1) }) { Text("Next"); Icon(Icons.Default.ChevronRight, null) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(170.dp).height(36.dp), contentAlignment = Alignment.CenterStart) {
                Text("CHANNEL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.weight(1f).horizontalScroll(timelineScrollState)) {
                repeat(dayHours) { hour ->
                    Box(Modifier.width((60 * scale).dp).height(36.dp), contentAlignment = Alignment.CenterStart) {
                        Text("%02d:00".format(hour), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF303943))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(visibleChannels, key = { it.id }) { channel ->
                val channelEpgId = mappingByStream[channel.id]?.xmltvChannelId ?: channel.tvgId
                val channelProgrammes = programmesByChannel[channelEpgId].orEmpty().filter {
                    it.startUtc < dayEnd && it.endUtc > dayStart
                }.sortedBy { it.startUtc }
                Row(Modifier.fillMaxWidth().height(78.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(170.dp).padding(end = 8.dp)) {
                        Text(channel.name, color = Color.White, fontSize = 12.sp, maxLines = 2)
                        Text(channel.category, color = Muted, fontSize = 10.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onPlay(channel) }, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                Text("Watch", fontSize = 10.sp)
                            }
                            TextButton(onClick = { mappingChannel = channel }, contentPadding = PaddingValues(0.dp)) {
                                Text("EPG map", fontSize = 10.sp)
                            }
                        }
                    }
                    Row(
                        Modifier.weight(1f).horizontalScroll(timelineScrollState)
                            .background(Color(0xFF10151B), RoundedCornerShape(7.dp))
                    ) {
                        if (channelProgrammes.isEmpty()) {
                            Box(Modifier.width((dayHours * 60 * scale).dp).height(72.dp), contentAlignment = Alignment.CenterStart) {
                                Text("No programme data for this channel", color = Muted, fontSize = 11.sp)
                            }
                        } else {
                            channelProgrammes.forEachIndexed { index, programme ->
                                val clippedStart = programme.startUtc.coerceAtLeast(dayStart)
                                val clippedEnd = programme.endUtc.coerceAtMost(dayEnd)
                                val gap = if (index == 0) clippedStart - dayStart else
                                    clippedStart - channelProgrammes[index - 1].endUtc.coerceAtMost(dayEnd)
                                if (gap > 0) Spacer(Modifier.width((gap / 60000.0 * scale.toDouble()).dp))
                                val width = (((clippedEnd - clippedStart) / 60000.0) * scale.toDouble())
                                    .toFloat().coerceAtLeast(48f)
                                Surface(
                                    onClick = { selectedProgramme = programme; selectedChannel = channel },
                                    color = if (System.currentTimeMillis() in programme.startUtc until programme.endUtc)
                                        Color(0xFF35485A) else Color(0xFF242D37),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.width(width.dp).height(72.dp).padding(end = 3.dp)
                                ) {
                                    Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.Center) {
                                        Text(
                                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(programme.startUtc)),
                                            color = Color(0xFFB8C6D3), fontSize = 9.sp
                                        )
                                        Text(programme.title, color = Color.White, fontSize = 11.sp, maxLines = 2)
                                        if (System.currentTimeMillis() in programme.startUtc until programme.endUtc) {
                                            val progress = ((System.currentTimeMillis() - programme.startUtc).toFloat() /
                                                (programme.endUtc - programme.startUtc).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                                            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                                        }
                                    }
                                }
                            }
                            val lastEnd = channelProgrammes.last().endUtc.coerceAtMost(dayEnd)
                            if (lastEnd < dayEnd) {
                                Spacer(Modifier.width(((dayEnd - lastEnd) / 60000.0 * scale.toDouble()).dp))
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF202832))
            }
        }
    }
    }

    mappingChannel?.let { channel ->
        val suggestions = remember(channel.id, epgChannels) { EpgMatching.suggest(channel, epgChannels) }
        val orderedChannels = remember(channel.id, epgChannels) {
            val scores = suggestions.associate { it.xmltvId to it.score }
            epgChannels.distinctBy { it.xmltvId }
                .sortedWith(compareByDescending<EpgChannelEntity> { scores[it.xmltvId] ?: 0 }.thenBy { it.displayName.lowercase() })
        }
        AlertDialog(
            onDismissRequest = { mappingChannel = null },
            title = { Text("Map EPG: ${channel.name}") },
            text = {
                if (orderedChannels.isEmpty()) Text("No XMLTV channel metadata available. Add an EPG source first.")
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(orderedChannels, key = { it.id }) { epg ->
                        TextButton(onClick = { onMap(channel.id, epg.xmltvId); mappingChannel = null }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(epg.displayName.ifBlank { epg.xmltvId })
                                val score = suggestions.firstOrNull { it.xmltvId == epg.xmltvId }
                                Text("${epg.xmltvId}${when { score?.exactId == true -> " • exact tvg-id"; score != null -> " • suggestion ${score.score}%"; else -> "" }}", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mappingChannel = null }) { Text("Close") } }
        )
    }

    selectedProgramme?.let { programme ->
        AlertDialog(
            onDismissRequest = { selectedProgramme = null },
            title = { Text(programme.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(selectedChannel?.name.orEmpty(), color = Muted)
                    Text(
                        "${java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(programme.startUtc))} – ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(programme.endUtc))}",
                        color = Muted, fontSize = 12.sp
                    )
                    Text(programme.description.ifBlank { "No programme description available." })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedChannel?.let(onPlay)
                    selectedProgramme = null
                }) { Text("Watch channel") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { selectedChannel?.let { onReminder(it, programme, 5) }; selectedProgramme = null }) { Text("Remind 5m") }
                    TextButton(onClick = { selectedChannel?.let { onReminder(it, programme, 10) }; selectedProgramme = null }) { Text("10m") }
                    TextButton(onClick = { selectedChannel?.let { onReminder(it, programme, 15) }; selectedProgramme = null }) { Text("15m") }
                    TextButton(onClick = { selectedProgramme = null }) { Text("Close") }
                }
            }
        )
    }
}


@Composable
private fun CompactGuideList(
    channels: List<StreamEntity>,
    programmesByChannel: Map<String, List<ProgrammeEntity>>,
    mappingByStream: Map<String, ChannelMappingEntity>,
    dayStart: Long,
    dayEnd: Long,
    dayLabel: String,
    reminders: List<ProgrammeReminderEntity>,
    onDayChange: (Int) -> Unit,
    onPlay: (StreamEntity) -> Unit,
    onProgrammeSelected: (StreamEntity, ProgrammeEntity) -> Unit,
    onMapChannel: (StreamEntity) -> Unit,
    onCancelReminder: (ProgrammeReminderEntity) -> Unit
) {
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reminders.isNotEmpty()) {
            val reminder = reminders.first()
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("UPCOMING REMINDER", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${reminder.programmeTitle} · ${reminder.minutesBefore} min",
                        color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { onCancelReminder(reminder) }) { Text("Cancel") }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { onDayChange(-1) }) { Icon(Icons.Default.ChevronLeft, "Previous day") }
            Text(dayLabel, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = { onDayChange(0) }) { Text("Today") }
            IconButton(onClick = { onDayChange(1) }) { Icon(Icons.Default.ChevronRight, "Next day") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 10.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                val epgId = mappingByStream[channel.id]?.xmltvChannelId?.takeIf { it.isNotBlank() } ?: channel.tvgId
                val channelProgrammes = programmesByChannel[epgId].orEmpty()
                    .filter { it.startUtc < dayEnd && it.endUtc > dayStart }
                    .sortedBy { it.startUtc }
                val current = channelProgrammes.firstOrNull { now >= it.startUtc && now < it.endUtc }
                val next = channelProgrammes.firstOrNull { it.startUtc >= (current?.endUtc ?: now) }
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(channel.category, color = Muted, fontSize = 10.sp, maxLines = 1)
                            }
                            TextButton(onClick = { onMapChannel(channel) }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                                Text("EPG map", fontSize = 11.sp)
                            }
                        }
                        if (current != null) {
                            Surface(
                                onClick = { onProgrammeSelected(channel, current) },
                                color = Color(0xFF26394B),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("NOW", color = Color(0xFFB8D8FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(current.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(current.startUtc))} – ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(current.endUtc))}",
                                        color = Muted, fontSize = 10.sp
                                    )
                                    val progress = ((now - current.startUtc).toFloat() / (current.endUtc - current.startUtc).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        } else {
                            Text("No current programme data", color = Muted, fontSize = 11.sp)
                        }
                        if (next != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("NEXT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(7.dp))
                                Text(next.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TextButton(onClick = { onPlay(channel) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Watch channel")
                        }
                    }
                }
            }
            if (channels.isEmpty()) {
                item { EmptyState("No matching channels", "Try another search or import an EPG source.") }
            }
        }
    }
}

@Composable
private fun EpgSourcesPanel(
    sources: List<EpgSourceEntity>,
    onAdd: (String, String) -> Unit,
    onRefresh: (EpgSourceEntity) -> Unit,
    onEnabled: (EpgSourceEntity, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("XMLTV SOURCES", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
        OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("XMLTV URL") })
        Button(onClick = { onAdd(name, url); name = ""; url = "" }, enabled = url.startsWith("http://") || url.startsWith("https://")) { Text("Add EPG source") }
        if (sources.isEmpty()) Text("No additional EPG sources configured.", color = Muted, fontSize = 11.sp)
        sources.forEach { source ->
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${source.status} • ${source.lastError.ifBlank { if (source.lastUpdatedAt > 0) java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(source.lastUpdatedAt)) else "Not imported" }}", color = Muted, fontSize = 10.sp, maxLines = 2)
                }
                Switch(checked = source.enabled, onCheckedChange = { onEnabled(source, it) })
                IconButton(onClick = { onRefresh(source) }) { Icon(Icons.Default.Refresh, "Refresh EPG") }
                IconButton(onClick = { onDelete(source.id) }) { Icon(Icons.Default.DeleteOutline, "Delete EPG source") }
            }
        }
    }
}

@Composable
private fun PlaylistManagerList(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistEntity>,
    onEnabled: (PlaylistEntity, Boolean) -> Unit,
    onSettings: (PlaylistEntity, String, Int, String) -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Pair<Long, Int>) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    if (playlists.isEmpty()) {
        Box(modifier) { EmptyState("No playlists", "Add an M3U or Xtream Codes source.") }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            playlists.forEach { playlist ->
                var playlistName by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }
                var epgUrl by remember(playlist.id, playlist.epgUrl) { mutableStateOf(playlist.epgUrl) }
                var refreshHours by remember(playlist.id, playlist.refreshIntervalHours) { mutableIntStateOf(playlist.refreshIntervalHours) }
                Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (playlist.type == "XTREAM") Icons.Default.Cloud else Icons.Default.PlaylistPlay,
                            null, tint = Color.LightGray
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${playlist.type} • ${if (playlist.lastRefreshAt > 0) "Updated " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(playlist.lastRefreshAt)) else "Not refreshed yet"}",
                                color = Muted, fontSize = 11.sp
                            )
                        }
                        Switch(checked = playlist.enabled, onCheckedChange = { onEnabled(playlist, it) })
                        IconButton(onClick = { onMove(playlist.id to -1) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { onMove(playlist.id to 1) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { onRefresh(playlist.id) }) { Icon(Icons.Default.Refresh, "Refresh") }
                        IconButton(onClick = { pendingDelete = playlist }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFE58B8B)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Refresh", color = Muted, fontSize = 11.sp)
                        listOf(1, 6, 12, 24).forEach { hours ->
                            FilterChip(
                                selected = refreshHours == hours,
                                onClick = { refreshHours = hours },
                                label = { Text("${hours}h") }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSettings(playlist, playlistName, refreshHours, epgUrl) }) { Text("Save settings") }
                    }
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 56.dp),
                        singleLine = true,
                        label = { Text("Playlist name") }
                    )
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 56.dp),
                        singleLine = true,
                        label = { Text("XMLTV EPG URL (optional)") }
                    )
                    }
                }
            }
        }
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("This removes ${item.name} and its imported channels from Ollie TV.") },
            confirmButton = {
                TextButton(onClick = { onDelete(item.id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ScheduleRecordingDialog(
    onDismiss: () -> Unit,
    onSchedule: (Long, Int) -> Unit
) {
    val defaultDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    var date by remember { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf("20:00") }
    var duration by remember { mutableStateOf("60") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule recording") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Start time (HH:mm)") }, singleLine = true)
                OutlinedTextField(duration, { duration = it }, label = { Text("Duration (minutes)") }, singleLine = true)
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = runCatching {
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply { isLenient = false }
                    format.parse("$date $time")!!.time
                }.getOrNull()
                val minutes = duration.toIntOrNull() ?: 0
                if (parsed == null || parsed <= System.currentTimeMillis()) error = "Choose a valid future date and time."
                else if (minutes !in 1..1440) error = "Duration must be between 1 and 1440 minutes."
                else onSchedule(parsed, minutes)
            }) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LiveTv, null, tint = Muted, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Surface(color = Panel, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) { Text(title); Text(subtitle, color = Muted, fontSize = 11.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit, onFile: () -> Unit,
    onUrl: (String, String) -> Unit, onXtream: (String, String, String, String) -> Unit
) {
    var mode by remember { mutableStateOf("M3U URL") }
    var name by remember { mutableStateOf("My playlist") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add IPTV source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabRow(selectedTabIndex = if (mode == "M3U URL") 0 else 1) {
                    Tab(selected = mode == "M3U URL", onClick = { mode = "M3U URL" }, text = { Text("M3U") })
                    Tab(selected = mode == "Xtream Codes", onClick = { mode = "Xtream Codes" }, text = { Text("Xtream") })
                }
                OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true)
                if (mode == "M3U URL") {
                    OutlinedTextField(url, { url = it }, label = { Text("M3U URL") }, singleLine = true)
                    TextButton(onClick = onFile) { Icon(Icons.Default.FolderOpen, null); Text("Choose M3U file") }
                } else {
                    OutlinedTextField(server, { server = it }, label = { Text("Server URL") }, singleLine = true)
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mode == "M3U URL") onUrl(name, url) else onXtream(name, server, user, pass)
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SimpleUrlDialog(title: String, label: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSubmit(value) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
