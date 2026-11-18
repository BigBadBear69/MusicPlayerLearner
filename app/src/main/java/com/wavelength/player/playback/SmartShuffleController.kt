package com.wavelength.player.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Lives in the playback service and keeps the player's shuffle order "smart":
 *  - when shuffle is turned on (or a new queue starts shuffled) it builds a [SmartShuffle] order that
 *    starts with the current song;
 *  - when a looping, shuffled queue reaches its last song, it plans the next round so the songs that
 *    just played don't come straight back, and the next round is in a new order.
 */
@UnstableApi
class SmartShuffleController(private val player: ExoPlayer) : Player.Listener {

    /** Optional per-song weighting (song id → weight); filled in by the learning features later. */
    var weightFor: (Long) -> Double = { 1.0 }

    fun attach() {
        player.setShuffleOrder(ListShuffleOrder(IntArray(0)))
        player.addListener(this)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled) reshuffle(lastPlayedAgo = null)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!player.shuffleModeEnabled || player.repeatMode != Player.REPEAT_MODE_ALL) return
        val timeline = player.currentTimeline
        if (timeline.isEmpty || player.mediaItemCount < 2) return
        val current = player.currentMediaItemIndex
        if (current != timeline.getLastWindowIndex(/* shuffleModeEnabled = */ true)) return

        // The last song of this round is playing: plan the next round now, so the song that follows is
        // already chosen with the no-repeat rule. The current song stays first in the new order.
        val previous = shuffledOrder(timeline)
        val ago = IntArray(player.mediaItemCount) { -1 }
        previous.forEachIndexed { position, index -> ago[index] = previous.size - 1 - position }
        reshuffle(lastPlayedAgo = ago)
    }

    /** Shuffles the queue with the current song first. */
    fun reshuffle(lastPlayedAgo: IntArray?) {
        val count = player.mediaItemCount
        if (count == 0) return
        val artists = List(count) { i ->
            player.getMediaItemAt(i).mediaMetadata.artist?.toString().orEmpty()
        }
        val current = player.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET }
        val order = SmartShuffle.buildOrder(
            artists = artists,
            first = current,
            lastPlayedAgo = lastPlayedAgo,
            weight = { i -> player.getMediaItemAt(i).mediaId.toLongOrNull()?.let(weightFor) ?: 1.0 },
        )
        player.setShuffleOrder(ListShuffleOrder(order))
    }

    private fun shuffledOrder(timeline: Timeline): IntArray {
        val out = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            out += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
        }
        return out.toIntArray()
    }
}
