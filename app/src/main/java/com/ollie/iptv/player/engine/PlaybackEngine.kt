package com.ollie.iptv.player.engine

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Owns the single app playback engine.
 *
 * The engine is owned by MainViewModel, not by a Compose view, so Activity
 * recreation (for example, a device rotation) does not release playback.
 */
class PlaybackEngine(context: Context) {
    private val appContext = context.applicationContext
    private var activePlayer: ExoPlayer? = null
    private var activeUrl: String? = null

    @Synchronized
    fun play(url: String): ExoPlayer {
        require(url.isNotBlank()) { "Playback URL must not be blank." }
        activePlayer?.let { player ->
            if (activeUrl != url) {
                activeUrl = url
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
            }
            player.playWhenReady = true
            return player
        }

        return ExoPlayer.Builder(appContext).build().also { player ->
            activeUrl = url
            activePlayer = player
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    @Synchronized
    fun release() {
        activePlayer?.release()
        activePlayer = null
        activeUrl = null
    }
}
