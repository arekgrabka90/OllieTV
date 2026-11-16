package com.ollie.iptv

import android.content.Intent
import android.net.Uri
import java.io.File
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
import androidx.compose.ui.viewinterop.AndroidView
import com.ollie.iptv.data.*
import com.ollie.iptv.recording.RecordingService
import com.ollie.iptv.ui.MainViewModel

private val Ink = Color(0xFF090B0E)
private val Panel = Color(0xFF15191F)
private val Muted = Color(0xFF929BA6)
private enum class ScreenTab(val label: String) { LIVE("Live TV"), GUIDE("TV Guide"), MOVIES("Movies"), SERIES("Series"), FAVORITES("Favorites"), RECORDINGS("Recordings"), PLAYLISTS("Playlists") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        val refreshRequest = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ollie_playlist_auto_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            refreshRequest
        )
        setContent { OllieApp() }
    }
}

@Composable
private fun OllieApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val live by vm.live.collectAsState(initial = emptyList())
    val movies by vm.movies.collectAsState(initial = emptyList())
    val series by vm.series.collectAsState(initial = emptyList())
    val favorites by vm.favorites.collectAsState(initial = emptyList())
    val recordings by vm.recordings.collectAsState(initial = emptyList())
    val playlists by vm.playlists.collectAsState(initial = emptyList())
    val guideProgrammes by vm.guideProgrammes.collectAsState()
    val guideDayOffset by vm.guideDayOffset.collectAsState()
    LaunchedEffect(Unit) { vm.loadGuideDay(0) }
    val episodes by vm.episodes.collectAsState()
    val selected by vm.selected.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    var tab by remember { mutableStateOf(ScreenTab.LIVE) }
    var dialog by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All channels") }
    var search by remember { mutableStateOf("") }
    var selectedSeries by remember { mutableStateOf<StreamEntity?>(null) }
    var catchupTarget by remember { mutableStateOf<StreamEntity?>(null) }
    var scheduleDialog by remember { mutableStateOf(false) }

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
        primary = Color(0xFFE9EDF2), background = Ink, surface = Panel,
        onBackground = Color(0xFFF4F6F8), onSurface = Color(0xFFF4F6F8),
        secondary = Color(0xFFB6C0CA)
    )) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (selected != null) {
                OlliePlayer(
                    stream = selected!!,
                    programmes = vm.programmes.collectAsState().value,
                    onBack = vm::stopPlayback,
                    channels = live,
                    onZap = vm::play,
                    onCatchup = { catchupTarget = selected },
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
                    }
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
                        onDismiss = { catchupTarget = null },
                        onSubmit = { date, time, duration ->
                            catchupTarget = null
                            vm.playCatchup(target, date, time, duration)
                        }
                    )
                }
            } else {
                Row(Modifier.fillMaxSize().padding(26.dp)) {
                    Column(Modifier.width(220.dp).fillMaxHeight()) {
                        Text("OLLIE", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("TELEVISION", color = Muted, fontSize = 10.sp, letterSpacing = 3.sp)
                        Spacer(Modifier.height(28.dp))
                        ScreenTab.values().forEach { item ->
                            NavigationItem(item, tab == item) { tab = item }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("OLLIE TV  •  0.4.0", color = Muted, fontSize = 10.sp)
                    }
                    Column(Modifier.fillMaxSize().padding(start = 26.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tab.label, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                Text(when(tab) {
                                    ScreenTab.LIVE -> "${live.size} channels"
                                    ScreenTab.MOVIES -> "${movies.size} movies"
                                    ScreenTab.SERIES -> if (selectedSeries == null) "${series.size} series" else "Episodes • ${selectedSeries?.name}"
                                    ScreenTab.FAVORITES -> "${favorites.size} favorites"
                                    ScreenTab.GUIDE -> "Programme schedule"
                                    ScreenTab.RECORDINGS -> "${recordings.size} recordings"
                                    ScreenTab.PLAYLISTS -> "${playlists.size} sources"
                                }, color = Muted, fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { dialog = "add" }) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add source")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { dialog = "epg" }) { Icon(Icons.Default.CalendarMonth, "Import EPG") }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (tab == ScreenTab.LIVE || tab == ScreenTab.GUIDE || tab == ScreenTab.MOVIES || tab == ScreenTab.SERIES || tab == ScreenTab.FAVORITES) {
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 58.dp),
                                placeholder = { Text("Search channels and programmes") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        when (tab) {
                            ScreenTab.LIVE -> {
                                val categories = listOf("All channels") + live.map { it.category }.distinct().sorted()
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    categories.take(7).forEach { cat ->
                                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                                    }
                                }
                                StreamList(live.filter { (category == "All channels" || it.category == category) && it.name.contains(search, true) },
                                    onPlay = vm::play, onFavorite = vm::toggleFavorite)
                            }
                            ScreenTab.MOVIES -> StreamList(movies.filter { it.name.contains(search, true) }, vm::play, vm::toggleFavorite)
                            ScreenTab.SERIES -> {
                                if (selectedSeries == null) {
                                    SeriesList(series.filter { it.name.contains(search, true) }, onSelect = {
                                        selectedSeries = it
                                        vm.loadSeries(it)
                                    }, onFavorite = vm::toggleFavorite)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { selectedSeries = null }) { Icon(Icons.Default.ArrowBack, null); Text("All series") }
                                    }
                                    StreamList(episodes, vm::play, vm::toggleFavorite)
                                }
                            }
                            ScreenTab.FAVORITES -> StreamList(favorites.filter { it.name.contains(search, true) }, vm::play, vm::toggleFavorite)
                            ScreenTab.GUIDE -> GuideList(
                                channels = live, programmes = guideProgrammes, query = search,
                                dayOffset = guideDayOffset, onDayChange = vm::loadGuideDay, onPlay = vm::play
                            )
                            ScreenTab.PLAYLISTS -> PlaylistManagerList(
                                playlists = playlists,
                                onEnabled = vm::setPlaylistEnabled,
                                onSettings = vm::updatePlaylistSettings,
                                onRefresh = vm::refreshPlaylist,
                                onDelete = vm::deletePlaylist,
                                onMove = { direction ->
                                    val ordered = playlists.toMutableList()
                                    val index = ordered.indexOfFirst { it.id == direction.first }
                                    val target = index + direction.second
                                    if (index >= 0 && target in ordered.indices) {
                                        val item = ordered.removeAt(index)
                                        ordered.add(target, item)
                                        vm.reorderPlaylists(ordered)
                                    }
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
}

@Composable
private fun NavigationItem(tab: ScreenTab, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Color(0xFF28313B) else Color.Transparent,
        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(when(tab) {
                ScreenTab.LIVE -> Icons.Default.Tv
                ScreenTab.GUIDE -> Icons.Default.CalendarMonth
                ScreenTab.MOVIES -> Icons.Default.Movie
                ScreenTab.SERIES -> Icons.Default.VideoLibrary
                ScreenTab.FAVORITES -> Icons.Default.Star
                ScreenTab.RECORDINGS -> Icons.Default.FiberManualRecord
                ScreenTab.PLAYLISTS -> Icons.Default.PlaylistPlay
            }, null, tint = if (selected) Color.White else Muted)
            Spacer(Modifier.width(12.dp))
            Text(tab.label, color = if (selected) Color.White else Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun StreamList(items: List<StreamEntity>, onPlay: (StreamEntity) -> Unit, onFavorite: (StreamEntity) -> Unit) {
    if (items.isEmpty()) { EmptyState("Nothing here yet", "Add an M3U playlist or Xtream account."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(items, key = { it.id }) { stream ->
            Surface(onClick = { onPlay(stream) }, color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFF252D36), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(if (stream.kind == "MOVIE") Icons.Default.Movie else Icons.Default.Tv, null, tint = Color.LightGray)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stream.name, fontWeight = FontWeight.SemiBold)
                        Text(stream.category, color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onFavorite(stream) }) {
                        Icon(if (stream.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            "Favorite", tint = if (stream.isFavorite) Color(0xFFFFD166) else Muted)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Muted)
                }
            }
        }
    }
}


@Composable
private fun SeriesList(items: List<StreamEntity>, onSelect: (StreamEntity) -> Unit, onFavorite: (StreamEntity) -> Unit) {
    if (items.isEmpty()) { EmptyState("No series", "Import an Xtream Codes source."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(items, key = { it.id }) { item ->
            Surface(onClick = { onSelect(item) }, color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VideoLibrary, null, tint = Color.LightGray)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(item.category, color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onFavorite(item) }) {
                        Icon(if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite")
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
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
    onPlay: (StreamEntity) -> Unit
) {
    var selectedProgramme by remember { mutableStateOf<ProgrammeEntity?>(null) }
    var selectedChannel by remember { mutableStateOf<StreamEntity?>(null) }
    val dayCalendar = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
    }
    val dayLabel = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(dayCalendar.time)
    val startCalendar = (dayCalendar.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val dayStart = startCalendar.timeInMillis
    val dayEnd = dayStart + 24L * 60L * 60L * 1000L
    val scale = 2.0f
    val timelineScrollState = rememberScrollState()
    val visibleChannels = channels.filter { it.name.contains(query, true) }
    if (channels.isEmpty()) { EmptyState("No channels", "Import a playlist first."); return }

    Column(Modifier.fillMaxSize()) {
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
                repeat(24) { hour ->
                    Box(Modifier.width((60 * scale).dp).height(36.dp), contentAlignment = Alignment.CenterStart) {
                        Text("%02d:00".format(hour), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF303943))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(visibleChannels, key = { it.id }) { channel ->
                val channelProgrammes = programmes.filter {
                    it.channelId == channel.tvgId && it.startUtc < dayEnd && it.endUtc > dayStart
                }.sortedBy { it.startUtc }
                Row(Modifier.fillMaxWidth().height(78.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(170.dp).padding(end = 8.dp)) {
                        Text(channel.name, color = Color.White, fontSize = 12.sp, maxLines = 2)
                        Text(channel.category, color = Muted, fontSize = 10.sp, maxLines = 1)
                        TextButton(onClick = { onPlay(channel) }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                            Text("Watch", fontSize = 10.sp)
                        }
                    }
                    Row(
                        Modifier.weight(1f).horizontalScroll(timelineScrollState)
                            .background(Color(0xFF10151B), RoundedCornerShape(7.dp))
                    ) {
                        if (channelProgrammes.isEmpty()) {
                            Box(Modifier.width((24 * 60 * scale).dp).height(72.dp), contentAlignment = Alignment.CenterStart) {
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
            dismissButton = { TextButton(onClick = { selectedProgramme = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun PlaylistManagerList(
    playlists: List<PlaylistEntity>,
    onEnabled: (PlaylistEntity, Boolean) -> Unit,
    onSettings: (PlaylistEntity, String, Int, String) -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Pair<Long, Int>) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    if (playlists.isEmpty()) {
        EmptyState("No playlists", "Add an M3U or Xtream Codes source.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(playlists, key = { it.id }) { playlist ->
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

@Composable
private fun OlliePlayer(
    stream: StreamEntity,
    programmes: List<ProgrammeEntity>,
    channels: List<StreamEntity>,
    onBack: () -> Unit,
    onZap: (StreamEntity) -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onSchedule: () -> Unit,
    onCatchup: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(stream.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(stream.url))
            prepare()
            playWhenReady = true
        }
    }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hudVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var audioLanguage by remember { mutableStateOf("Audio: Auto") }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    val focusRequester = remember { FocusRequester() }
    val channelIndex = channels.indexOfFirst { it.id == stream.id }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = error.message ?: "Playback failed"
                hudVisible = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(hudVisible) {
        if (hudVisible) {
            kotlinx.coroutines.delay(7000)
            hudVisible = false
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            kotlinx.coroutines.delay(750)
        }
    }

    val modifier = Modifier.fillMaxSize()
        .focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val code = event.key.nativeKeyCode
            when (code) {
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_ESCAPE -> {
                    onBack()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!hudVisible) {
                        hudVisible = true
                        true
                    } else false
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (hudVisible) false
                    else {
                        hudVisible = true
                        when (code) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                if (channelIndex > 0) onZap(channels[channelIndex - 1])
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (channelIndex >= 0 && channelIndex < channels.lastIndex) onZap(channels[channelIndex + 1])
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val end = player.duration
                                player.seekTo(if (end > 0) (player.currentPosition + 10_000L).coerceAtMost(end) else player.currentPosition + 10_000L)
                            }
                        }
                        true
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (player.isPlaying) player.pause() else player.play()
                    hudVisible = true
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); hudVisible = true; true }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); hudVisible = true; true }
                else -> false
            }
        }
        .focusable()

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    playerView.value = this
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
                playerView.value = view
            },
            modifier = Modifier.fillMaxSize()
        )

        if (hudVisible) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color(0xCC090D12)).padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(stream.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${stream.category}  •  ${if (player.isPlaying) "PLAYING" else "PAUSED"}", color = Color(0xFFB7C2CC), fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        val languages = player.currentTracks.groups
                            .filter { it.type == C.TRACK_TYPE_AUDIO }
                            .flatMap { group ->
                                (0 until group.length).filter { group.isTrackSupported(it) }
                                    .mapNotNull { group.getTrackFormat(it).language }
                            }.distinct()
                        if (languages.isNotEmpty()) {
                            val next = languages[(languages.indexOf(player.trackSelectionParameters.preferredAudioLanguages.firstOrNull()).let { if (it < 0) 0 else (it + 1) % languages.size })]
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setPreferredAudioLanguage(next).build()
                            audioLanguage = "Audio: $next"
                        } else audioLanguage = "Audio: unavailable"
                    }) { Text(audioLanguage, color = Color.White, fontSize = 11.sp) }
                    IconButton(onClick = {
                        subtitlesEnabled = !subtitlesEnabled
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
                    }) {
                        Icon(Icons.Default.Subtitles, "Toggle subtitles", tint = if (subtitlesEnabled) Color.White else Muted)
                    }
                    IconButton(onClick = {
                        resizeMode = when (resizeMode) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }) { Icon(Icons.Default.AspectRatio, "Aspect ratio", tint = Color.White) }
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xE6090D12)).padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                programmes.firstOrNull()?.let { current ->
                    Text("NOW  •  ${current.title}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (current.description.isNotBlank()) {
                        Text(current.description, color = Color(0xFFB7C2CC), fontSize = 11.sp, maxLines = 2)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPlayerTime(position), color = Color.White, fontSize = 11.sp)
                    LinearProgressIndicator(
                        progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(4.dp)
                    )
                    Text(if (duration > 0) formatPlayerTime(duration) else "LIVE", color = Color.White, fontSize = 11.sp)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (channelIndex > 0) onZap(channels[channelIndex - 1]) },
                        enabled = channelIndex > 0
                    ) { Icon(Icons.Default.SkipPrevious, "Previous channel") }
                    Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause")
                        Spacer(Modifier.width(5.dp)); Text(if (player.isPlaying) "Pause" else "Play")
                    }
                    OutlinedButton(
                        onClick = { if (channelIndex >= 0 && channelIndex < channels.lastIndex) onZap(channels[channelIndex + 1]) },
                        enabled = channelIndex >= 0 && channelIndex < channels.lastIndex
                    ) { Icon(Icons.Default.SkipNext, "Next channel") }
                    OutlinedButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                        Icon(Icons.Default.Replay10, "Back 10 seconds")
                    }
                    OutlinedButton(onClick = { player.seekTo(player.currentPosition + 10_000L) }) {
                        Icon(Icons.Default.Forward10, "Forward 10 seconds")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onRecord) { Icon(Icons.Default.FiberManualRecord, null); Text("Record") }
                    OutlinedButton(onClick = onSchedule) { Icon(Icons.Default.Schedule, null); Text("Schedule") }
                    OutlinedButton(onClick = onStopRecord) { Icon(Icons.Default.Stop, null); Text("Stop") }
                    if (stream.catchup && stream.xtreamId >= 0) {
                        OutlinedButton(onClick = onCatchup) { Text("Catch-up") }
                    }
                }
            }
        }

        playbackError?.let {
            Surface(
                Modifier.align(Alignment.Center).padding(24.dp),
                color = Color(0xDD551111), shape = RoundedCornerShape(10.dp)
            ) {
                Text("Playback error: $it", color = Color.White, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val total = (milliseconds / 1000L).coerceAtLeast(0L)
    return if (total >= 3600L) {
        "%02d:%02d:%02d".format(total / 3600L, (total / 60L) % 60L, total % 60L)
    } else {
        "%02d:%02d".format(total / 60L, total % 60L)
    }
}
