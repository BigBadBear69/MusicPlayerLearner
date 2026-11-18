package com.wavelength.player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.library.Library
import com.wavelength.player.ui.components.AddToPlaylistSheet
import com.wavelength.player.ui.components.AlbumArt
import com.wavelength.player.ui.components.MiniPlayer
import com.wavelength.player.ui.components.PlaylistNameDialog
import com.wavelength.player.ui.components.SongActionsSheet
import com.wavelength.player.ui.components.SongMenu
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.screens.CollectionScreen
import com.wavelength.player.ui.screens.LibraryActions
import com.wavelength.player.ui.screens.LibraryScreen
import com.wavelength.player.ui.screens.NowPlayingScreen
import com.wavelength.player.ui.screens.PlaylistScreen
import com.wavelength.player.ui.screens.QueueSheet
import com.wavelength.player.ui.screens.AddSongsScreen
import com.wavelength.player.ui.screens.DiscoverActions
import com.wavelength.player.ui.screens.DiscoverScreen
import com.wavelength.player.ui.screens.SettingsScreen
import com.wavelength.player.ui.screens.SongStatsSheet
import com.wavelength.player.ui.screens.StatsScreen
import kotlinx.coroutines.delay

private const val NOW_PLAYING = "now_playing"

@Composable
fun WavelengthRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val library by vm.library.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val songsById = remember(library) { library?.songs?.associateBy { it.id }.orEmpty() }
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val songStats by vm.songStats.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Sheets and dialogs shown over any screen.
    var songMenu by remember { mutableStateOf<SongMenu?>(null) }
    var addToPlaylist by remember { mutableStateOf<List<SongEntity>?>(null) }
    var newPlaylistFor by remember { mutableStateOf<List<SongEntity>?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf<PlaylistUi?>(null) }
    var statsFor by remember { mutableStateOf<SongEntity?>(null) }
    val statsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportStats)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importPlaylist)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val p = exporting
        if (uri != null && p != null) vm.exportPlaylist(p, uri)
        exporting = null
    }

    // Poll the playback position a few times a second while something is playing.
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player.isPlaying, player.songId) {
        do {
            positionMs = vm.player.positionMs
            delay(250)
        } while (player.isPlaying)
    }

    val currentArt: AlbumArt? = player.songId?.let { songsById[it]?.art() }
    val showMini = player.hasSong && route != NOW_PLAYING
    val listPadding = PaddingValues(bottom = if (showMini) 96.dp else 16.dp)

    val actions = LibraryActions(
        play = { songs, i, shuffle -> vm.player.play(songs, i, shuffle) },
        more = { songMenu = it },
        openPlaylist = { nav.navigate("playlist/$it") },
        newPlaylist = { newPlaylistFor = emptyList() },
        openStats = { nav.navigate("stats") },
        openDiscover = { nav.navigate("discover") },
        openSettings = { nav.navigate("settings") },
        openFavorites = { nav.navigate("favorites") },
        importPlaylist = { importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "*/*")) },
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
                            library, playlists, favoriteIds.size, isScanning, player.songId, query, results,
                            vm::setQuery, vm::rescan, actions, listPadding,
                        )
                    }
                }
                composable("playlist/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull()
                    val playlist = playlists.find { it.id == id }
                    if (playlist == null) {
                        LaunchedEffect(Unit) { delay(300); if (playlists.none { it.id == id }) nav.popBackStack() }
                        return@composable
                    }
                    Box(Modifier.statusBarsPadding()) {
                        PlaylistScreen(
                            playlist, player.songId,
                            onPlay = actions.play,
                            onMore = actions.more,
                            onReorder = { vm.reorderPlaylist(playlist, it) },
                            onRename = { vm.renamePlaylist(playlist.id, it) },
                            onDelete = { nav.popBackStack(); vm.deletePlaylist(playlist) },
                            onExport = { exporting = playlist; exportLauncher.launch("${playlist.name}.m3u8") },
                            onAddSongs = { nav.navigate("playlist/${playlist.id}/add") },
                            onBack = { nav.popBackStack() },
                            contentPadding = listPadding,
                        )
                    }
                }
                composable("playlist/{id}/add") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull()
                    val playlist = playlists.find { it.id == id } ?: return@composable
                    Box(Modifier.statusBarsPadding()) {
                        AddSongsScreen(
                            playlistName = playlist.name,
                            library = library,
                            alreadyInPlaylist = remember(playlist) { playlist.entries.mapTo(HashSet()) { it.song.id } },
                            favoriteIds = favoriteIds,
                            onAdd = { vm.addSongsToPlaylist(playlist.id, it) },
                            onClose = { nav.popBackStack() },
                        )
                    }
                }
                composable("discover") {
                    val discover by vm.discover.collectAsStateWithLifecycle()
                    val items by vm.discoverItems.collectAsStateWithLifecycle()
                    val covers by vm.deezerInfo.collectAsStateWithLifecycle()
                    val preview by vm.previewState.collectAsStateWithLifecycle()
                    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    LaunchedEffect(library) { if (library != null) vm.maybeAutoRefreshDiscover() }
                    LaunchedEffect(wishlist) { vm.resolveWishlistCovers(wishlist.map { it.first }) }
                    Box(Modifier.statusBarsPadding()) {
                        DiscoverScreen(
                            discover, settings.lastFmApiKey.isNotBlank(), settings.discoverOnline, items, covers, preview, wishlist,
                            DiscoverActions(
                                refresh = vm::refreshDiscover,
                                togglePreview = vm::togglePreview,
                                stopPreview = vm::stopPreview,
                                thumbsUp = vm::thumbsUp,
                                thumbsDown = vm::thumbsDown,
                                toggleWishlist = vm::toggleWishlist,
                                removeFromWishlist = { vm.removeFromWishlist(it) },
                                playLocal = { vm.player.play(listOf(it)) },
                                openSettings = { nav.navigate("settings") },
                                previewProgress = { (vm.previewPositionMs.toFloat() / vm.previewDurationMs).coerceIn(0f, 1f) },
                            ),
                            onBack = { nav.popBackStack() },
                            contentPadding = listPadding,
                        )
                    }
                }
                composable("settings") {
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    Box(Modifier.statusBarsPadding()) {
                        SettingsScreen(
                            settings,
                            onSaveKey = vm::setLastFmKey,
                            onTestKey = { vm.testLastFmKey(it) },
                            onDiscoverOnline = vm::setDiscoverOnline,
                            onClearDiscoverCache = vm::clearDiscoverCache,
                            onRescan = vm::rescan,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("stats") {
                    val snapshot by vm.stats.collectAsStateWithLifecycle()
                    val range by vm.statsRange.collectAsStateWithLifecycle()
                    val recent by vm.recentListens.collectAsStateWithLifecycle()
                    Box(Modifier.statusBarsPadding()) {
                        StatsScreen(
                            snapshot, range, recent, songsById,
                            onRange = vm::setStatsRange,
                            onOpenSong = { statsFor = it },
                            onOpenArtist = actions.openArtist,
                            onExport = { statsExportLauncher.launch("wavelength-listening-history.csv") },
                            onReset = vm::resetStats,
                            onBack = { nav.popBackStack() },
                            contentPadding = listPadding,
                        )
                    }
                }
                composable("favorites") {
                    val favs = favoriteIds.mapNotNull { songsById[it] }
                    Box(Modifier.statusBarsPadding()) {
                        CollectionScreen(
                            "Favourites", "Newest first", favs, null, false,
                            player.songId, actions.play, actions.more, onBack = { nav.popBackStack() }, contentPadding = listPadding,
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
                        onOpenQueue = { showQueue = true },
                        isFavorite = player.songId in favoriteIds,
                        onToggleFavorite = { player.songId?.let { songsById[it] }?.let(vm::toggleFavorite) },
                        onShowStats = { statsFor = player.songId?.let { songsById[it] } },
                    )
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                SnackbarHost(snackbar)
                if (showMini) {
                    MiniPlayer(
                        state = player,
                        art = currentArt,
                        progress = if (player.durationMs > 0) (positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f,
                        onOpen = { nav.navigate(NOW_PLAYING) { launchSingleTop = true } },
                        onPlayPause = vm.player::togglePlayPause,
                        onNext = vm.player::next,
                    )
                }
            }
        }
    }

    songMenu?.let { menu ->
        val single = menu.songs.singleOrNull()
        val album = single?.let { s -> library?.albums?.find { it.id == s.albumId } }
        SongActionsSheet(
            menu = menu,
            onDismiss = { songMenu = null },
            onPlayNext = { vm.playNext(menu.songs) },
            onAddToQueue = { vm.addToQueue(menu.songs) },
            onAddToPlaylist = { songMenu = null; addToPlaylist = menu.songs },
            onGoToAlbum = album?.let { a -> { nav.navigate("album/${a.id}") } },
            onGoToArtist = single?.let { s -> { nav.navigate("artist/${Uri.encode(s.artist)}") } },
            onRemoveFromPlaylist = menu.playlist?.let { p ->
                menu.playlistPosition?.let { pos -> { vm.removeFromPlaylist(p, pos) } }
            },
            isFavorite = single?.let { it.id in favoriteIds },
            onToggleFavorite = single?.let { s -> { vm.toggleFavorite(s) } },
            onShowStats = single?.let { s -> { statsFor = s } },
        )
    }
    addToPlaylist?.let { songs ->
        AddToPlaylistSheet(
            songs, playlists,
            onDismiss = { addToPlaylist = null },
            onPick = { vm.addToPlaylist(it, songs) },
            onNew = { addToPlaylist = null; newPlaylistFor = songs },
        )
    }
    newPlaylistFor?.let { songs ->
        PlaylistNameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { newPlaylistFor = null },
            onConfirm = { name ->
                vm.createPlaylist(name, songs, open = if (songs.isEmpty()) { id -> nav.navigate("playlist/$id") } else null)
            },
        )
    }
    statsFor?.let { song ->
        val events by remember(song.id) { vm.eventsForSong(song.id) }.collectAsStateWithLifecycle(emptyList())
        SongStatsSheet(
            song = song,
            stats = songStats[song.id],
            events = events,
            onPlay = { vm.player.play(listOf(song)) },
            onToggleFavorite = { vm.toggleFavorite(song) },
            onDismiss = { statsFor = null },
        )
    }
    if (showQueue && player.hasSong) {
        QueueSheet(
            upNext = player.upNext,
            queueSize = player.queueSize,
            shuffle = player.shuffle,
            songsById = songsById,
            onJump = vm.player::jumpTo,
            onRemove = vm.player::removeFromQueue,
            onDismiss = { showQueue = false },
        )
    }
}

private data class CollectionData(
    val title: String,
    val subtitle: String,
    val songs: List<SongEntity>,
    val cover: AlbumArt?,
    val numbered: Boolean,
)

private fun NavGraphBuilder.collection(
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
            currentSongId, actions.play, actions.more, onBack = { nav.popBackStack() }, contentPadding = padding,
        )
    }
}
