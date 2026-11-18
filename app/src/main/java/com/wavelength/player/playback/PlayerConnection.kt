package com.wavelength.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.wavelength.player.data.db.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val songId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueSize: Int = 0,
) {
    val hasSong: Boolean get() = songId != null
}

/** The UI's handle on the background [PlaybackService]. */
class PlayerConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: (MediaController.() -> Unit)? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { f ->
            f.addListener({
                val c = runCatching { f.get() }.getOrNull() ?: return@addListener
                controller = c
                c.addListener(listener)
                publish(c)
                pending?.invoke(c)
                pending = null
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun withController(block: MediaController.() -> Unit) {
        controller?.block() ?: run { pending = block; connect() }
    }

    val positionMs: Long get() = controller?.currentPosition ?: 0L

    fun play(songs: List<SongEntity>, startIndex: Int = 0, shuffle: Boolean = false) = withController {
        if (songs.isEmpty()) return@withController
        shuffleModeEnabled = shuffle
        val start = if (shuffle && startIndex == 0) songs.indices.random() else startIndex
        setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
        prepare()
        play()
    }

    fun togglePlayPause() = withController { if (isPlaying) pause() else { if (playbackState == Player.STATE_IDLE) prepare(); play() } }
    fun next() = withController { seekToNext() }
    fun previous() = withController { seekToPrevious() }
    fun seekTo(ms: Long) = withController { seekTo(ms) }
    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }
    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun publish(p: Player) {
        val item = p.currentMediaItem
        val md = p.mediaMetadata
        _state.value = PlayerState(
            songId = item?.mediaId?.toLongOrNull(),
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            album = md.albumTitle?.toString().orEmpty(),
            artworkUri = md.artworkUri,
            isPlaying = p.isPlaying,
            durationMs = p.duration.coerceAtLeast(0),
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            queueSize = p.mediaItemCount,
        )
    }
}

fun SongEntity.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setGenre(genre)
                .setTrackNumber(trackNumber.takeIf { it > 0 })
                .setReleaseYear(year.takeIf { it > 0 })
                .setDurationMs(durationMs)
                .setArtworkUri(uri) // AlbumArtBitmapLoader reads the art embedded in the file
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
