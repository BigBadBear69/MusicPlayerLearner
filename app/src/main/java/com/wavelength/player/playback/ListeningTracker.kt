package com.wavelength.player.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Watches the player and reports every listen: which song, how long it was actually heard (paused time
 * doesn't count), and whether it ended by itself or because you moved on.
 */
class ListeningTracker(
    private val player: Player,
    private val report: (Listen) -> Unit,
) : Player.Listener {

    data class Listen(
        val songId: Long,
        val startedAt: Long,
        val endedAt: Long,
        val listenedMs: Long,
        val durationMs: Long,
        val endedNaturally: Boolean,
        val replay: Boolean,
    )

    private class Session(val songId: Long, val startedAt: Long, val durationMs: Long, val replay: Boolean) {
        var listenedMs = 0L
        var playingSince: Long? = null
        fun pause() { playingSince?.let { listenedMs += SystemClock.elapsedRealtime() - it }; playingSince = null }
        fun resume() { if (playingSince == null) playingSince = SystemClock.elapsedRealtime() }
        fun total() = listenedMs + (playingSince?.let { SystemClock.elapsedRealtime() - it } ?: 0)
    }

    private var session: Session? = null

    fun attach() {
        player.addListener(this)
        start(player.currentMediaItem, replay = false)
    }

    /** Call when the service shuts down so the last listen isn't lost. */
    fun flush() = finish(endedNaturally = false)

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val natural = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        val previous = session?.songId
        finish(endedNaturally = natural)
        val id = mediaItem?.mediaId?.toLongOrNull()
        start(mediaItem, replay = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT || (id != null && id == previous))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val s = session ?: return start(player.currentMediaItem, replay = false)
        if (isPlaying) s.resume() else s.pause()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) finish(endedNaturally = true)
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // "Previous" restarting the same song counts as finishing this listen and starting a replay.
        if (reason == Player.DISCONTINUITY_REASON_SEEK &&
            oldPosition.mediaItemIndex == newPosition.mediaItemIndex &&
            newPosition.positionMs < 2_000 && oldPosition.positionMs > 15_000
        ) {
            finish(endedNaturally = false)
            start(player.currentMediaItem, replay = true)
        }
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        if (timeline.isEmpty) finish(endedNaturally = false)
    }

    private fun start(item: MediaItem?, replay: Boolean) {
        if (session != null) return
        val id = item?.mediaId?.toLongOrNull() ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: item.mediaMetadata.durationMs ?: 0L
        session = Session(id, System.currentTimeMillis(), duration, replay).also {
            if (player.isPlaying) it.resume()
        }
    }

    private fun finish(endedNaturally: Boolean) {
        val s = session ?: return
        session = null
        s.pause()
        // Duration may only be known once the file was opened.
        val duration = s.durationMs.takeIf { it > 0 } ?: player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        report(Listen(s.songId, s.startedAt, System.currentTimeMillis(), s.listenedMs, duration, endedNaturally, s.replay))
    }
}
