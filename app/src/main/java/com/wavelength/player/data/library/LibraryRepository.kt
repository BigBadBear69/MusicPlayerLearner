package com.wavelength.player.data.library

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.wavelength.player.data.db.SongDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibraryRepository(
    private val context: Context,
    private val songDao: SongDao,
    private val scope: CoroutineScope,
) {
    private val scanner = MediaScanner(context.contentResolver)
    private val scanMutex = Mutex()
    private var observer: ContentObserver? = null
    private var pendingRescan: Job? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val library: StateFlow<Library?> = songDao.observeAll()
        .map { Library.from(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Rescans the device. Safe to call repeatedly; overlapping calls run one at a time. */
    suspend fun rescan() = scanMutex.withLock {
        _isScanning.value = true
        try {
            val songs = kotlinx.coroutines.withContext(Dispatchers.IO) { scanner.scan() }
            songDao.sync(songs)
        } finally {
            _isScanning.value = false
        }
    }

    /** Call once the audio permission is granted: scans now, then whenever files change. */
    fun startWatching() {
        scope.launch { rescan() }
        if (observer != null) return
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // MediaStore fires many events while files are copied; wait for it to settle.
                pendingRescan?.cancel()
                pendingRescan = scope.launch {
                    delay(2_000)
                    rescan()
                }
            }
        }.also {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, it,
            )
        }
    }
}
