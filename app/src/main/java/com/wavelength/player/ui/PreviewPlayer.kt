package com.wavelength.player.ui

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreviewState(val key: String? = null, val loading: Boolean = false, val playing: Boolean = false, val failed: String? = null)

/**
 * Plays 30-second Discover previews on its own small player, separate from your queue.
 * Asking for audio focus pauses the main player while a preview plays.
 * [onFinished] reports how long each preview was listened to (that feeds back into Discover).
 */
class PreviewPlayer(context: Context, private val onFinished: (key: String, listenedMs: Long) -> Unit) {
    private val player = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .build()
    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private var listened = 0L
    private var playingSince: Long? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playingSince = SystemClock.elapsedRealtime()
                else playingSince?.let { listened += SystemClock.elapsedRealtime() - it; playingSince = null }
                _state.value = _state.value.copy(playing = isPlaying, loading = false)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) stop()
            }

            override fun onPlayerError(error: PlaybackException) {
                val key = _state.value.key
                finish()
                _state.value = PreviewState(failed = key)
            }
        })
    }

    val positionMs: Long get() = player.currentPosition
    val durationMs: Long get() = player.duration.takeIf { it > 0 } ?: 30_000

    fun loading(key: String) {
        finish()
        _state.value = PreviewState(key = key, loading = true)
    }

    fun play(key: String, url: String) {
        if (_state.value.key != key) finish()
        _state.value = PreviewState(key = key, loading = true)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    fun notFound(key: String) {
        _state.value = PreviewState(failed = key)
    }

    fun stop() {
        finish()
        _state.value = PreviewState()
    }

    private fun finish() {
        val key = _state.value.key ?: return
        playingSince?.let { listened += SystemClock.elapsedRealtime() - it; playingSince = null }
        player.stop()
        player.clearMediaItems()
        if (listened > 0) onFinished(key, listened)
        listened = 0
    }

    fun release() {
        finish()
        player.release()
    }
}
