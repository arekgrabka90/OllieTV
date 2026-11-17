package com.ollie.iptv.player.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.theme.Muted
import kotlinx.coroutines.delay

@Composable
fun OllieMiniPlayer(
    player: ExoPlayer,
    stream: StreamEntity,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    compact: Boolean = false
) {
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    val view = remember(player) { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying
            delay(450)
        }
    }

    DisposableEffect(player) {
        onDispose { view.value?.player = null }
    }

    Surface(
        onClick = onExpand,
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 10.dp),
        color = Color(0xF0211E2D),
        shape = RoundedCornerShape(if (compact) 16.dp else 24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .15f)),
        shadowElevation = if (compact) 8.dp else 18.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(if (compact) 5.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            if (compact) {
                Box(
                    Modifier.size(38.dp).background(Color(0xFF49301F), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tv, null, tint = Color(0xFFFF8A32))
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            view.value = this
                        }
                    },
                    update = { it.player = player; view.value = it },
                    modifier = Modifier.width(110.dp).height(64.dp)
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("NOW PLAYING", color = Color(0xFFFF8A32), fontSize = 8.sp, letterSpacing = 1.1.sp, maxLines = 1)
                Text(stream.name, color = Color.White, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
                if (!compact) Text(stream.category, color = Muted, fontSize = 10.sp, maxLines = 1)
            }

            IconButton(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                    playing = player.isPlaying
                },
                modifier = Modifier.size(if (compact) 38.dp else 48.dp)
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White)
            }
            IconButton(onClick = onExpand, modifier = Modifier.size(if (compact) 38.dp else 48.dp)) {
                Icon(Icons.Default.Fullscreen, "Full screen", tint = Color(0xFFFF8A32))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(if (compact) 38.dp else 48.dp)) {
                Icon(Icons.Default.Close, "Stop", tint = Muted)
            }
        }
    }
}
