package com.ollie.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ollie.iptv.data.ProgrammeEntity
import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.theme.Muted

@Composable
fun OlliePlayer(
    player: ExoPlayer,
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
    var playbackError by remember { mutableStateOf<String?>(null) }
    var hudVisible by rememberSaveable { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var resizeMode by rememberSaveable { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var audioLanguage by rememberSaveable { mutableStateOf("Audio: Auto") }
    var subtitlesEnabled by rememberSaveable { mutableStateOf(true) }
    var zapOverlay by remember { mutableStateOf<StreamEntity?>(null) }
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
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
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
                    if (hudVisible) false
                    else {
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
                val nowUtc = System.currentTimeMillis()
                val currentProgramme = programmes.firstOrNull { nowUtc >= it.startUtc && nowUtc < it.endUtc }
                val nextProgramme = programmes.firstOrNull { it.startUtc >= (currentProgramme?.endUtc ?: nowUtc) }
                if (currentProgramme != null) {
                    Text("NOW  •  ${currentProgramme.title}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    val programmeProgress = ((nowUtc - currentProgramme.startUtc).toFloat() /
                        (currentProgramme.endUtc - currentProgramme.startUtc).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { programmeProgress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(3.dp)
                    )
                    if (currentProgramme.description.isNotBlank()) {
                        Text(currentProgramme.description, color = Color(0xFFB7C2CC), fontSize = 11.sp, maxLines = 2)
                    }
                } else {
                    Text("NOW  •  No programme data", color = Color(0xFFB7C2CC), fontSize = 11.sp)
                }
                nextProgramme?.let { next ->
                    Text(
                        "NEXT  •  ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(next.startUtc))}  ${next.title}",
                        color = Color(0xFFB7C2CC), fontSize = 11.sp, maxLines = 1
                    )
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

        zapOverlay?.let { target ->
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                color = Color(0xE6090D12),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tv, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("QUICK ZAP", color = Color(0xFFB7C2CC), fontSize = 9.sp)
                        Text(target.name, color = Color.White, fontWeight = FontWeight.SemiBold)
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
