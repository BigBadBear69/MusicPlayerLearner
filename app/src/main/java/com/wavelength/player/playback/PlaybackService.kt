package com.wavelength.player.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.wavelength.player.MainActivity
import com.wavelength.player.WavelengthApp
import com.wavelength.player.data.db.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the player. Runs in the background and provides the notification and lock-screen controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var queueStore: QueueStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saveJob: Job? = null
    private lateinit var tracker: ListeningTracker

    override fun onCreate() {
        super.onCreate()
        queueStore = QueueStore(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        SmartShuffleController(player).attach()

        // Every listen goes to the stats database (written on the app scope so it survives the service).
        val app = application as WavelengthApp
        tracker = ListeningTracker(player) { l ->
            app.appScope.launch {
                runCatching { app.stats.record(l.songId, l.startedAt, l.endedAt, l.listenedMs, l.durationMs, l.endedNaturally, l.replay) }
            }
        }.also { it.attach() }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setBitmapLoader(CacheBitmapLoader(AlbumArtBitmapLoader(contentResolver)))
            .setCallback(SessionCallback())
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) scheduleSave()
            }
        })
        // Save the position now and then while playing, so a crash or reboot loses at most a few seconds.
        scope.launch {
            while (isActive) {
                delay(15_000)
                if (player.isPlaying) saveQueue()
            }
        }
        scope.launch { restoreQueue(prepare = true) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        tracker.flush()
        saveQueue()
        scope.cancel()
        session?.release()
        player.release()
        session = null
        super.onDestroy()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            saveQueue()
        }
    }

    private fun saveQueue() {
        val n = player.mediaItemCount
        val ids = List(n) { player.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        val order = (player.shuffleOrder as? ListShuffleOrder)?.order?.takeIf { it.size == n }
        val q = SavedQueue(ids, player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode, order)
        scope.launch(Dispatchers.IO) { runCatching { queueStore.save(q) } }
    }

    /** Loads the saved queue as playable items, dropping songs that no longer exist. */
    private suspend fun loadSavedItems(): Pair<SavedQueue, List<MediaItem>>? {
        val saved = withContext(Dispatchers.IO) { queueStore.load() } ?: return null
        if (saved.songIds.isEmpty()) return null
        val songs: Map<Long, SongEntity> = withContext(Dispatchers.IO) {
            (application as WavelengthApp).database.songDao().byIds(saved.songIds).associateBy { it.id }
        }
        if (songs.size != saved.songIds.size) {
            // Some files are gone: keep what's left, without the old shuffle order (it no longer lines up).
            val items = saved.songIds.mapNotNull { songs[it]?.toMediaItem() }
            if (items.isEmpty()) return null
            val currentId = saved.songIds.getOrNull(saved.index)
            val idx = items.indexOfFirst { it.mediaId == currentId.toString() }
            return saved.copy(index = idx.coerceAtLeast(0), positionMs = if (idx >= 0) saved.positionMs else 0, shuffleOrder = null) to items
        }
        return saved to saved.songIds.map { songs.getValue(it).toMediaItem() }
    }

    private suspend fun restoreQueue(prepare: Boolean) {
        if (player.mediaItemCount > 0) return
        val (saved, items) = loadSavedItems() ?: return
        if (player.mediaItemCount > 0) return // something started playing meanwhile
        applySaved(saved, items)
        if (prepare) player.prepare()
    }

    private fun applySaved(saved: SavedQueue, items: List<MediaItem>) {
        player.setMediaItems(items, saved.index.coerceIn(0, items.size - 1), saved.positionMs)
        player.repeatMode = saved.repeatMode
        player.shuffleModeEnabled = saved.shuffle
        saved.shuffleOrder?.takeIf { it.size == items.size }?.let { player.setShuffleOrder(ListShuffleOrder(it)) }
    }

    private inner class SessionCallback : MediaSession.Callback {
        // Items arriving from a controller may lack their playable URI; rebuild it from the song id.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlayableUri() }.toMutableList())

        // Bluetooth / headset "play" after the app was closed: continue where the user left off.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val loaded = runCatching { loadSavedItems() }.getOrNull()
                if (loaded == null) {
                    result.setException(IllegalStateException("Nothing to resume"))
                } else {
                    val (saved, items) = loaded
                    result.set(MediaSession.MediaItemsWithStartPosition(items, saved.index, saved.positionMs))
                }
            }
            return result
        }
    }

    private fun MediaItem.withPlayableUri(): MediaItem {
        if (localConfiguration != null) return this
        val id = mediaId.toLongOrNull() ?: return this
        return buildUpon()
            .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
            .build()
    }
}
