package com.ollie.iptv.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ollie.iptv.data.ProgrammeEntity
import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.theme.Muted

private val Glass = Color(0xE61C1A2A)
private val GlassStrong = Color(0xF0211E2D)
private val GlassLine = Color(0xFF393343)
private val TextSecondary = Color(0xFFA5A1B2)
private val Accent = Color(0xFFFF8A32)
private val LiveRed = Color(0xFFF04452)

@Composable
fun OlliePlayer(
    player: ExoPlayer,
    stream: StreamEntity,
    programmes: List<ProgrammeEntity>,
    allProgrammes: List<ProgrammeEntity> = programmes,
    guideProgrammes: List<ProgrammeEntity> = allProgrammes,
    guideDayOffset: Int = 0,
    onGuideDayChange: (Int) -> Unit = {},
    channelMappings: List<com.ollie.iptv.data.ChannelMappingEntity> = emptyList(),
    channels: List<StreamEntity>,
    onBack: () -> Unit,
    onZap: (StreamEntity) -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onSchedule: () -> Unit,
    onCatchup: () -> Unit,
    hudTimeoutSeconds: Int = 7
) {
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hudVisible by rememberSaveable { mutableStateOf(true) }
    var epgVisible by rememberSaveable { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var resizeMode by rememberSaveable { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var audioLanguage by rememberSaveable { mutableStateOf("Audio: Auto") }
    var subtitlesEnabled by rememberSaveable { mutableStateOf(true) }
    var zapOverlay by remember { mutableStateOf<StreamEntity?>(null) }
    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    val focusRequester = remember { FocusRequester() }
    val channelIndex = channels.indexOfFirst { it.id == stream.id }
    var browsedChannel by remember(stream.id) { mutableStateOf(stream) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = error.message ?: "Playback failed"
                hudVisible = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(hudVisible, epgVisible, hudTimeoutSeconds) {
        if (hudVisible && !epgVisible) {
            kotlinx.coroutines.delay(hudTimeoutSeconds.coerceIn(3, 15) * 1000L)
            hudVisible = false
        }
    }
    LaunchedEffect(zapOverlay) {
        if (zapOverlay != null) {
            kotlinx.coroutines.delay(1800)
            zapOverlay = null
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
                    if (epgVisible) { epgVisible = false; hudVisible = true } else onBack()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (epgVisible) false else if (!hudVisible) { hudVisible = true; true } else false
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (epgVisible) return@onPreviewKeyEvent false
                    val targetIndex = if (code == android.view.KeyEvent.KEYCODE_DPAD_UP) channelIndex - 1 else channelIndex + 1
                    if (channelIndex >= 0 && targetIndex in channels.indices) {
                        val target = channels[targetIndex]
                        zapOverlay = target
                        hudVisible = true
                        onZap(target)
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (epgVisible) return@onPreviewKeyEvent true
                    val targetIndex = if (code == android.view.KeyEvent.KEYCODE_CHANNEL_UP) channelIndex - 1 else channelIndex + 1
                    if (channelIndex >= 0 && targetIndex in channels.indices) {
                        val target = channels[targetIndex]
                        zapOverlay = target
                        hudVisible = true
                        onZap(target)
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (epgVisible || hudVisible) false else {
                        hudVisible = true
                        if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        } else {
                            val end = player.duration
                            player.seekTo(if (end > 0) (player.currentPosition + 10_000L).coerceAtMost(end) else player.currentPosition + 10_000L)
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
            modifier = Modifier.fillMaxSize().pointerInput(player) {
                detectTapGestures { if (!epgVisible) hudVisible = !hudVisible }
            }
        )

        AnimatedVisibility(
            visible = hudVisible && !epgVisible,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xD9000000), Color(0x00000000))))
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 34.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Glass,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, GlassLine),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassIconButton(Icons.Default.ArrowBack, "Back", onBack)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("OLLIE  /  LIVE TV", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(stream.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stream.category.ifBlank { "Live television" }, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusPill(if (player.isPlaying) "LIVE" else "PAUSED", if (player.isPlaying) LiveRed else TextSecondary)
                        GlassIconButton(Icons.Default.Tv, "TV guide", { epgVisible = true; hudVisible = false })
                        GlassIconButton(Icons.Default.GraphicEq, audioLanguage, {
                            val languages = player.currentTracks.groups
                                .filter { it.type == C.TRACK_TYPE_AUDIO }
                                .flatMap { group ->
                                    (0 until group.length).filter { group.isTrackSupported(it) }
                                        .mapNotNull { group.getTrackFormat(it).language }
                                }.distinct()
                            if (languages.isNotEmpty()) {
                                val current = player.trackSelectionParameters.preferredAudioLanguages.firstOrNull()
                                val index = languages.indexOf(current)
                                val next = languages[if (index < 0) 0 else (index + 1) % languages.size]
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(next).build()
                                audioLanguage = "Audio: $next"
                            } else audioLanguage = "Audio unavailable"
                        })
                        GlassIconButton(
                            Icons.Default.Subtitles, "Toggle subtitles", {
                                subtitlesEnabled = !subtitlesEnabled
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
                            }, selected = subtitlesEnabled
                        )
                        GlassIconButton(Icons.Default.AspectRatio, "Change aspect ratio", {
                            resizeMode = when (resizeMode) {
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hudVisible && !epgVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xE6000000))))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                val nowUtc = System.currentTimeMillis()
                val currentProgramme = programmes.firstOrNull { nowUtc >= it.startUtc && nowUtc < it.endUtc }
                val nextProgramme = programmes.firstOrNull { it.startUtc >= (currentProgramme?.endUtc ?: nowUtc) }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GlassStrong,
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, GlassLine),
                    shadowElevation = 18.dp
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("NOW PLAYING", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                                Text(currentProgramme?.title ?: "No programme information", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (currentProgramme?.description?.isNotBlank() == true) {
                                    Text(currentProgramme.description, color = TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                StatusPill("ON AIR", LiveRed)
                                nextProgramme?.let {
                                    Text("NEXT  ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.startUtc))}", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(it.title, color = Color.White.copy(alpha = .82f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (currentProgramme != null) {
                            val programmeProgress = ((nowUtc - currentProgramme.startUtc).toFloat() /
                                (currentProgramme.endUtc - currentProgramme.startUtc).coerceAtLeast(1L)).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { programmeProgress },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = Accent,
                                trackColor = Color.White.copy(alpha = .12f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(formatPlayerTime(position), color = TextSecondary, fontSize = 10.sp)
                            LinearProgressIndicator(
                                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.weight(1f).height(4.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = .16f)
                            )
                            Text(if (duration > 0) formatPlayerTime(duration) else "LIVE", color = TextSecondary, fontSize = 10.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassIconButton(Icons.Default.SkipPrevious, "Previous channel", { if (channelIndex > 0) onZap(channels[channelIndex - 1]) }, enabled = channelIndex > 0)
                            GlassIconButton(Icons.Default.Replay10, "Back 10 seconds", { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) })
                            Surface(
                                onClick = { if (player.isPlaying) player.pause() else player.play() },
                                shape = CircleShape,
                                color = Accent,
                                contentColor = Color(0xFF0A111A),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = .45f)),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", modifier = Modifier.size(29.dp))
                                }
                            }
                            GlassIconButton(Icons.Default.Forward10, "Forward 10 seconds", { player.seekTo(player.currentPosition + 10_000L) })
                            GlassIconButton(Icons.Default.SkipNext, "Next channel", { if (channelIndex >= 0 && channelIndex < channels.lastIndex) onZap(channels[channelIndex + 1]) }, enabled = channelIndex >= 0 && channelIndex < channels.lastIndex)
                            Spacer(Modifier.weight(1f))
                            GlassIconButton(Icons.Default.FiberManualRecord, "Start recording", onRecord, selected = true, accent = LiveRed)
                            GlassIconButton(Icons.Default.Schedule, "Schedule recording", onSchedule)
                            GlassIconButton(Icons.Default.Stop, "Stop recording", onStopRecord)
                            if (stream.catchup && stream.xtreamId >= 0) GlassIconButton(Icons.Default.History, "Catch-up", onCatchup)
                        }
                    }
                }
            }
        }

        if (epgVisible) {
            Surface(
                Modifier.fillMaxSize().padding(18.dp),
                color = Color(0xF20A0F16),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, GlassLine),
                shadowElevation = 24.dp
            ) {
                Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.widthIn(min = 170.dp, max = 270.dp).fillMaxHeight()) {
                        Text("OLLIE  /  GUIDE", color = Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Television", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                        Text("CHANNELS", color = Muted, fontSize = 9.sp, letterSpacing = 1.6.sp)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(channels.size) { i ->
                                val item = channels[i]
                                val active = item.id == browsedChannel.id
                                Surface(
                                    onClick = { browsedChannel = item },
                                    color = if (active) Color(0xFF26394B) else Color.White.copy(alpha = .035f),
                                    shape = RoundedCornerShape(13.dp),
                                    border = if (active) BorderStroke(1.dp, Accent.copy(alpha = .35f)) else null
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (active) Box(Modifier.size(6.dp).background(Accent, CircleShape))
                                        else Box(Modifier.size(6.dp).background(Color.White.copy(alpha = .18f), CircleShape))
                                        Spacer(Modifier.width(9.dp))
                                        Text(item.name, color = if (active) Color.White else TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(browsedChannel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("PROGRAMME SCHEDULE  •  ${if (browsedChannel.id == stream.id) "NOW PLAYING" else "PREVIEW"}", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                            }
                            GuideDayButton("‹", { onGuideDayChange((guideDayOffset - 1).coerceAtLeast(0)) }, enabled = guideDayOffset > 0)
                            GuideDayButton("TODAY", { onGuideDayChange(0) }, enabled = guideDayOffset != 0)
                            GuideDayButton("›", { onGuideDayChange((guideDayOffset + 1).coerceAtMost(6)) }, enabled = guideDayOffset < 6)
                            if (browsedChannel.id != stream.id) {
                                Button(onClick = { onZap(browsedChannel); epgVisible = false; hudVisible = true }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0A111A))) {
                                    Text("WATCH", fontWeight = FontWeight.Bold)
                                }
                            }
                            GlassIconButton(Icons.Default.Close, "Close guide", { epgVisible = false; hudVisible = true })
                        }
                        Spacer(Modifier.height(14.dp))
                        val mappedEpgId = channelMappings.firstOrNull { it.streamId == browsedChannel.id }
                            ?.xmltvChannelId?.takeIf { it.isNotBlank() } ?: browsedChannel.tvgId
                        val visibleProgrammes = guideProgrammes.filter { it.channelId.equals(mappedEpgId, ignoreCase = true) }.sortedBy { it.startUtc }
                        if (visibleProgrammes.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CalendarMonth, null, tint = Muted, modifier = Modifier.size(34.dp))
                                    Text("No programme data available", color = TextSecondary, fontSize = 14.sp)
                                    Text("Check the EPG source or channel mapping", color = Muted, fontSize = 11.sp)
                                }
                            }
                        } else androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visibleProgrammes.size) { i ->
                                val pr = visibleProgrammes[i]
                                val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val now = System.currentTimeMillis()
                                val active = now >= pr.startUtc && now < pr.endUtc
                                Surface(
                                    color = if (active) Color(0xFF1C3041) else Color.White.copy(alpha = .045f),
                                    shape = RoundedCornerShape(15.dp),
                                    border = if (active) BorderStroke(1.dp, Accent.copy(alpha = .32f)) else BorderStroke(1.dp, Color.White.copy(alpha = .055f))
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                        Column(Modifier.width(88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(fmt.format(java.util.Date(pr.startUtc)), color = if (active) Accent else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(fmt.format(java.util.Date(pr.endUtc)), color = Muted, fontSize = 10.sp)
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (active) Text("NOW ON AIR", color = Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                                            Text(pr.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            if (pr.description.isNotBlank()) Text(pr.description, color = TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        zapOverlay?.let { target ->
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 92.dp),
                color = GlassStrong,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, GlassLine),
                shadowElevation = 18.dp
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tv, null, tint = Accent)
                    Spacer(Modifier.width(11.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("QUICK CHANNEL", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(target.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }

        playbackError?.let {
            Surface(
                Modifier.align(Alignment.Center).padding(24.dp),
                color = Color(0xE638171B),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, LiveRed.copy(alpha = .35f))
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = LiveRed)
                    Text("Playback error: $it", color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    GlassIconButton(Icons.Default.Close, "Dismiss error", { playbackError = null })
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    accent: Color = Accent
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = when {
            !enabled -> Color.White.copy(alpha = .025f)
            selected -> accent.copy(alpha = .17f)
            else -> Color.White.copy(alpha = .065f)
        },
        contentColor = when {
            !enabled -> Muted.copy(alpha = .45f)
            selected -> accent
            else -> Color.White
        },
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = .36f) else GlassLine.copy(alpha = .7f)),
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun StatusPill(label: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = .13f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, tint.copy(alpha = .30f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(5.dp).background(tint, CircleShape))
            Text(label, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        }
    }
}

@Composable
private fun GuideDayButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (enabled) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .025f),
        contentColor = if (enabled) Color.White else Muted.copy(alpha = .45f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, GlassLine)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
