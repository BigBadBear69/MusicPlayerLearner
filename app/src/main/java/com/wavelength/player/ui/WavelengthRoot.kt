package com.wavelength.player.ui

import android.net.Uri
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wavelength.player.data.library.Library
import com.wavelength.player.ui.components.AlbumArt
import com.wavelength.player.ui.components.MiniPlayer
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.screens.CollectionScreen
import com.wavelength.player.ui.screens.LibraryActions
import com.wavelength.player.ui.screens.LibraryScreen
import com.wavelength.player.ui.screens.NowPlayingScreen
import kotlinx.coroutines.delay

private const val NOW_PLAYING = "now_playing"

@Composable
fun WavelengthRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val library by vm.library.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    // Poll the playback position a few times a second while something is playing.
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player.isPlaying, player.songId) {
        do {
            positionMs = vm.player.positionMs
            delay(250)
        } while (player.isPlaying)
    }

    val currentArt: AlbumArt? = player.songId?.let { id -> library?.songs?.find { it.id == id }?.art() }
    val showMini = player.hasSong && route != NOW_PLAYING
    val listPadding = PaddingValues(bottom = if (showMini) 96.dp else 16.dp)

    val actions = LibraryActions(
        play = { songs, i, shuffle -> vm.player.play(songs, i, shuffle) },
        openAlbum = { nav.navigate("album/$it") },
        openArtist = { nav.navigate("artist/${Uri.encode(it)}") },
        openFolder = { nav.navigate("folder/${Uri.encode(it)}") },
        openGenre = { nav.navigate("genre/${Uri.encode(it)}") },
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = "library", Modifier.fillMaxSize()) {
                composable("library") {
                    Box(Modifier.statusBarsPadding()) {
                        LibraryScreen(
                            library, isScanning, player.songId, query, results,
                            vm::setQuery, vm::rescan, actions, listPadding,
                        )
                    }
                }
                collection(nav, "album/{key}", library, player.songId, actions, listPadding) { lib, key ->
                    lib.albums.find { it.id.toString() == key }?.let {
                        CollectionData(it.title, listOfNotNull(it.artist, it.year.takeIf { y -> y > 0 }?.toString()).joinToString(" · "), it.songs, it.coverSong.art(), true)
                    }
                }
                collection(nav, "artist/{key}", library, player.songId, actions, listPadding) { lib, key ->
                    lib.artists.find { it.name == key }?.let { CollectionData(it.name, "Artist", it.songs, it.songs.first().art(), false) }
                }
                collection(nav, "folder/{key}", library, player.songId, actions, listPadding) { lib, key ->
                    lib.folders.find { it.path == key }?.let { CollectionData(it.name, it.path, it.songs, null, false) }
                }
                collection(nav, "genre/{key}", library, player.songId, actions, listPadding) { lib, key ->
                    lib.genres.find { it.name == key }?.let { CollectionData(it.name, "Genre", it.songs, null, false) }
                }
                composable(
                    NOW_PLAYING,
                    enterTransition = { slideInVertically { it } },
                    exitTransition = { slideOutVertically { it } },
                    popEnterTransition = { slideInVertically { it } },
                    popExitTransition = { slideOutVertically { it } },
                ) {
                    LaunchedEffect(player.hasSong) { if (!player.hasSong) nav.popBackStack() }
                    NowPlayingScreen(
                        player, currentArt, positionMs,
                        onCollapse = { nav.popBackStack() },
                        onPlayPause = vm.player::togglePlayPause,
                        onNext = vm.player::next,
                        onPrevious = vm.player::previous,
                        onSeek = { positionMs = it; vm.player.seekTo(it) },
                        onToggleShuffle = vm.player::toggleShuffle,
                        onCycleRepeat = vm.player::cycleRepeat,
                    )
                }
            }

            if (showMini) {
                MiniPlayer(
                    state = player,
                    art = currentArt,
                    progress = if (player.durationMs > 0) (positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f,
                    onOpen = { nav.navigate(NOW_PLAYING) { launchSingleTop = true } },
                    onPlayPause = vm.player::togglePlayPause,
                    onNext = vm.player::next,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        }
    }
}

private data class CollectionData(
    val title: String,
    val subtitle: String,
    val songs: List<com.wavelength.player.data.db.SongEntity>,
    val cover: AlbumArt?,
    val numbered: Boolean,
)

private fun androidx.navigation.NavGraphBuilder.collection(
    nav: NavHostController,
    route: String,
    library: Library?,
    currentSongId: Long?,
    actions: LibraryActions,
    padding: PaddingValues,
    resolve: (Library, String) -> CollectionData?,
) = composable(route) { entry ->
    val key = entry.arguments?.getString("key")?.let(Uri::decode).orEmpty()
    val data = library?.let { resolve(it, key) }
    if (data == null) {
        // The item disappeared (e.g. files deleted during a rescan).
        LaunchedEffect(library) { if (library != null) nav.popBackStack() }
        return@composable
    }
    Box(Modifier.statusBarsPadding()) {
        CollectionScreen(
            data.title, data.subtitle, data.songs, data.cover, data.numbered,
            currentSongId, actions.play, onBack = { nav.popBackStack() }, contentPadding = padding,
        )
    }
}
