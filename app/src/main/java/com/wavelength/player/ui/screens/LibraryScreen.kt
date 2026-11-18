@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavelength.player.R
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.library.Album
import com.wavelength.player.data.library.Artist
import com.wavelength.player.data.library.Library
import com.wavelength.player.ui.PlaylistUi
import com.wavelength.player.ui.SearchResults
import com.wavelength.player.ui.components.SongMenu
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.IconTile
import com.wavelength.player.ui.components.ListRow
import com.wavelength.player.ui.components.PlayShuffleBar
import com.wavelength.player.ui.components.SongRow
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.songCount
import kotlinx.coroutines.launch

/** Callbacks shared by the library and detail screens. */
class LibraryActions(
    /** index null = start from a random song. */
    val play: (songs: List<SongEntity>, index: Int?, shuffle: Boolean) -> Unit,
    val more: (SongMenu) -> Unit,
    val openPlaylist: (Long) -> Unit,
    val newPlaylist: () -> Unit,
    val openStats: () -> Unit,
    val openDiscover: () -> Unit,
    val openSettings: () -> Unit,
    val openFavorites: () -> Unit,
    val importPlaylist: () -> Unit,
    val openAlbum: (Long) -> Unit,
    val openArtist: (String) -> Unit,
    val openFolder: (String) -> Unit,
    val openGenre: (String) -> Unit,
)

private enum class LibraryTab(val label: String) { Playlists("Playlists"), Songs("Songs"), Albums("Albums"), Artists("Artists"), Folders("Folders"), Genres("Genres") }

@Composable
fun LibraryScreen(
    library: Library?,
    playlists: List<PlaylistUi>,
    favoritesCount: Int,
    isScanning: Boolean,
    currentSongId: Long?,
    query: String,
    results: SearchResults,
    onQueryChange: (String) -> Unit,
    onRescan: () -> Unit,
    actions: LibraryActions,
    contentPadding: PaddingValues,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    // Open on Songs; Playlists is one swipe to the left.
    val pager = rememberPagerState(initialPage = LibraryTab.Songs.ordinal) { LibraryTab.entries.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        if (searching) {
            SearchBar(query, onQueryChange, onClose = { searching = false; onQueryChange("") })
        } else {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text("Wavelength", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = actions.openDiscover) { Icon(Icons.Rounded.AutoAwesome, "Discover new music", tint = MaterialTheme.colorScheme.tertiary) }
                    IconButton(onClick = actions.openStats) { Icon(Icons.Rounded.Insights, "Your stats") }
                    IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, "Search") }
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rescan music") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                enabled = !isScanning, onClick = { menu = false; onRescan() })
                            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                onClick = { menu = false; actions.openSettings() })
                        }
                    }
                },
            )
        }
        if (isScanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary)
        }

        when {
            searching && query.isNotBlank() ->
                SearchResultsList(results, currentSongId, actions, contentPadding)

            library == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            library.songs.isEmpty() -> EmptyLibrary(isScanning, onRescan)

            else -> {
                PrimaryScrollableTabRow(
                    selectedTabIndex = pager.currentPage,
                    edgePadding = 12.dp,
                    containerColor = Color.Transparent,
                ) {
                    LibraryTab.entries.forEachIndexed { i, tab ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            text = { Text(tab.label) },
                        )
                    }
                }
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    when (LibraryTab.entries[page]) {
                        LibraryTab.Playlists -> PlaylistsTab(playlists, favoritesCount, actions, contentPadding)
                        LibraryTab.Songs -> SongsTab(library.songs, currentSongId, actions, contentPadding)
                        LibraryTab.Albums -> AlbumGrid(library.albums, actions.openAlbum, contentPadding)
                        LibraryTab.Artists -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                            items(library.artists, key = { it.name }) { ArtistRow(it, actions.openArtist) }
                        }
                        LibraryTab.Folders -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                            items(library.folders, key = { it.path }) { f ->
                                ListRow(f.name, "${f.path.ifEmpty { "/" }} · ${songCount(f.songs.size)}", { actions.openFolder(f.path) }) {
                                    IconTile(Icons.Rounded.Folder)
                                }
                            }
                        }
                        LibraryTab.Genres -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                            if (library.genres.isEmpty()) item { EmptyNote("None of your songs have genre tags.") }
                            items(library.genres, key = { it.name }) { g ->
                                ListRow(g.name, songCount(g.songs.size), { actions.openGenre(g.name) }) {
                                    IconTile(Icons.Rounded.Category)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") } },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Songs, albums, artists") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear") }
                },
            )
        },
    )
}

@Composable
private fun SearchResultsList(results: SearchResults, currentSongId: Long?, actions: LibraryActions, contentPadding: PaddingValues) {
    if (results.isEmpty) {
        EmptyNote("No matches.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (results.artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            items(results.artists, key = { "ar:" + it.name }) { ArtistRow(it, actions.openArtist) }
        }
        if (results.albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            items(results.albums, key = { "al:" + it.id }) { a ->
                ListRow(a.title, "${a.artist} · ${songCount(a.songs.size)}", { actions.openAlbum(a.id) }) {
                    ArtImage(a.coverSong.art(), Modifier.size(48.dp))
                }
            }
        }
        if (results.songs.isNotEmpty()) {
            item { SectionHeader("Songs") }
            itemsIndexed(results.songs, key = { _, s -> "s:" + s.id }) { i, s ->
                SongRow(s, s.id == currentSongId, onClick = { actions.play(results.songs, i, false) }, onMore = { actions.more(s.menu()) })
            }
        }
    }
}

@Composable
private fun SongsTab(songs: List<SongEntity>, currentSongId: Long?, actions: LibraryActions, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            PlayShuffleBar(onPlay = { actions.play(songs, 0, false) }, onShuffle = { actions.play(songs, null, true) })
            Text(
                songCount(songs.size),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, s.id == currentSongId, onClick = { actions.play(songs, i, false) }, onMore = { actions.more(s.menu()) })
        }
    }
}

fun SongEntity.menu() = SongMenu(listOf(this), title, "$artist · $album", art())

@Composable
private fun PlaylistsTab(playlists: List<PlaylistUi>, favoritesCount: Int, actions: LibraryActions, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ListRow("Favourites", "${songCount(favoritesCount)} · tap ♥ on Now Playing to add", actions.openFavorites) {
                IconTile(Icons.Rounded.Favorite)
            }
            ListRow("New playlist", "Start an empty playlist", actions.newPlaylist) { IconTile(Icons.Rounded.Add) }
            ListRow("Import playlist", "From an .m3u or .m3u8 file", actions.importPlaylist) { IconTile(Icons.Rounded.FileDownload) }
        }
        if (playlists.isEmpty()) item { EmptyNote("No playlists yet. Create one, or tap ⋮ on any song and choose \"Add to playlist\".") }
        items(playlists, key = { it.id }) { p ->
            ListRow(p.name, songCount(p.entries.size), { actions.openPlaylist(p.id) }) {
                ArtImage(p.songs.firstOrNull()?.art(), Modifier.size(48.dp))
            }
        }
    }
}

@Composable
fun AlbumGrid(albums: List<Album>, onOpen: (Long) -> Unit, contentPadding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp, top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { a ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(a.id) }) {
                ArtImage(a.coverSong.art(), Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(12.dp), iconSize = 40.dp)
                Spacer(Modifier.height(6.dp))
                Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    a.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: Artist, onOpen: (String) -> Unit) {
    val albums = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums"
    ListRow(artist.name, "$albums · ${songCount(artist.songs.size)}", { onOpen(artist.name) }) {
        ArtImage(artist.songs.first().art(), Modifier.size(48.dp), CircleShape)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyLibrary(isScanning: Boolean, onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(if (isScanning) "Looking for music…" else "No music found", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Wavelength shows audio files stored on this phone, like MP3, FLAC and M4A, that are at least 20 seconds long.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isScanning) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRescan) { Text("Scan again") }
        }
    }
}
