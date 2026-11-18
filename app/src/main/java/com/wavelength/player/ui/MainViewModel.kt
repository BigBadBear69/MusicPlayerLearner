package com.wavelength.player.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wavelength.player.WavelengthApp
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.library.Album
import com.wavelength.player.data.library.Artist
import com.wavelength.player.data.library.Library
import com.wavelength.player.playback.PlayerConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResults(
    val songs: List<SongEntity> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/** A playlist joined with the library. [entries] pairs each song with its slot in the saved playlist. */
data class PlaylistUi(
    val id: Long,
    val name: String,
    val entries: List<PlaylistEntry>,
    /** Saved song ids whose files aren't on the phone right now; kept so they come back if the file does. */
    val missingIds: List<Long>,
) {
    val songs: List<SongEntity> get() = entries.map { it.song }
}

data class PlaylistEntry(val position: Int, val song: SongEntity)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WavelengthApp).library
    private val playlistRepo = (app as WavelengthApp).playlists

    val library: StateFlow<Library?> = repo.library
    val isScanning: StateFlow<Boolean> = repo.isScanning

    val player = PlayerConnection(app).also { it.connect() }

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Short confirmations shown at the bottom of the screen ("Added to …"). */
    val messages: Flow<String> = _messages.receiveAsFlow()
    private fun say(text: String) { _messages.trySend(text) }

    // ---- Search ----

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<SearchResults> =
        combine(_query.debounce(150), repo.library) { q, lib -> search(q.trim(), lib) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) { _query.value = q }

    private fun search(q: String, lib: Library?): SearchResults {
        if (q.isEmpty() || lib == null) return SearchResults()
        fun String?.hit() = this?.contains(q, ignoreCase = true) == true
        return SearchResults(
            songs = lib.songs.filter { it.title.hit() || it.artist.hit() || it.album.hit() }.take(100),
            albums = lib.albums.filter { it.title.hit() || it.artist.hit() }.take(20),
            artists = lib.artists.filter { it.name.hit() }.take(20),
        )
    }

    // ---- Library ----

    fun onPermissionGranted() = repo.startWatching()

    fun rescan() { viewModelScope.launch { repo.rescan() } }

    // ---- Playlists ----

    val playlists: StateFlow<List<PlaylistUi>> =
        combine(playlistRepo.playlists, repo.library) { summaries, lib ->
            val byId = lib?.songs?.associateBy { it.id }.orEmpty()
            summaries.map { s ->
                val ids = s.songIds
                PlaylistUi(
                    id = s.id,
                    name = s.name,
                    entries = ids.mapIndexedNotNull { pos, id -> byId[id]?.let { PlaylistEntry(pos, it) } },
                    missingIds = if (lib == null) emptyList() else ids.filter { it !in byId },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createPlaylist(name: String, songs: List<SongEntity> = emptyList(), open: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = playlistRepo.create(name, songs.map { it.id })
            say(if (songs.isEmpty()) "Created \"${name.trim()}\"" else "Created \"${name.trim()}\" with ${songCount(songs.size)}")
            open?.invoke(id)
        }
    }

    fun addToPlaylist(playlist: PlaylistUi, songs: List<SongEntity>) {
        viewModelScope.launch {
            playlistRepo.add(playlist.id, songs.map { it.id })
            say("Added ${if (songs.size == 1) "\"${songs[0].title}\"" else songCount(songs.size)} to ${playlist.name}")
        }
    }

    fun removeFromPlaylist(playlist: PlaylistUi, position: Int) {
        viewModelScope.launch { playlistRepo.removeAt(playlist.id, position) }
    }

    fun reorderPlaylist(playlist: PlaylistUi, songIds: List<Long>) {
        viewModelScope.launch { playlistRepo.setOrder(playlist.id, songIds + playlist.missingIds) }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepo.rename(id, name) }
    }

    fun deletePlaylist(playlist: PlaylistUi) {
        viewModelScope.launch {
            playlistRepo.delete(playlist.id)
            say("Deleted \"${playlist.name}\"")
        }
    }

    fun exportPlaylist(playlist: PlaylistUi, target: Uri) {
        viewModelScope.launch {
            runCatching { playlistRepo.exportM3u(getApplication<Application>().contentResolver, target, playlist.songs) }
                .onSuccess { say("Exported \"${playlist.name}\"") }
                .onFailure { say("Export failed: ${it.message}") }
        }
    }

    fun importPlaylist(source: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val name = runCatching {
                resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()?.substringBeforeLast('.') ?: "Imported playlist"
            runCatching { playlistRepo.importM3u(resolver, source, name, library.value?.songs.orEmpty()) }
                .onSuccess { (_, missing) ->
                    say(if (missing == 0) "Imported \"$name\"" else "Imported \"$name\" ($missing songs not found on this phone)")
                }
                .onFailure { say("Import failed: ${it.message}") }
        }
    }

    // ---- Queue ----

    fun playNext(songs: List<SongEntity>) {
        player.playNext(songs)
        say(if (songs.size == 1) "\"${songs[0].title}\" plays next" else "${songCount(songs.size)} play next")
    }

    fun addToQueue(songs: List<SongEntity>) {
        player.addToQueue(songs)
        say(if (songs.size == 1) "Added \"${songs[0].title}\" to the queue" else "Added ${songCount(songs.size)} to the queue")
    }

    override fun onCleared() {
        player.release()
    }
}
