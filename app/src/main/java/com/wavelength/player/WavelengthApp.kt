package com.wavelength.player

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.wavelength.player.data.db.WavelengthDatabase
import com.wavelength.player.data.library.LibraryRepository
import com.wavelength.player.data.library.PlaylistRepository
import com.wavelength.player.data.stats.StatsRepository
import com.wavelength.player.data.settings.SettingsStore
import com.wavelength.player.data.discover.DiscoverRepository
import com.wavelength.player.data.audio.AudioEffects
import com.wavelength.player.data.lyrics.LyricsRepository
import com.wavelength.player.playback.SleepTimer
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.wavelength.player.ui.components.AlbumArtFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WavelengthApp : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { WavelengthDatabase.create(this) }
    val library by lazy { LibraryRepository(this, database.songDao(), appScope) }
    val playlists by lazy { PlaylistRepository(database.playlistDao()) }
    val stats by lazy { StatsRepository(database) }
    val settings by lazy { SettingsStore(this) }
    val discover by lazy { DiscoverRepository(database, settings, appScope) }
    val audioEffects by lazy { AudioEffects(this) }
    val sleepTimer = SleepTimer()
    val lyrics by lazy { LyricsRepository(this, database.lyricsDao(), settings) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AlbumArtFetcher.Factory(contentResolver))
                add(AlbumArtFetcher.AlbumKeyer())
                add(OkHttpNetworkFetcherFactory()) // Discover cover art
            }
            .crossfade(true)
            .build()
}
