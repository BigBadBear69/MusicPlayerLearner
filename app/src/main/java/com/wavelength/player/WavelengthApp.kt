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

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(AlbumArtFetcher.Factory(contentResolver)); add(AlbumArtFetcher.AlbumKeyer()) }
            .crossfade(true)
            .build()
}
