package com.wavelength.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wavelength.player.WavelengthApp
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.library.Library
import com.wavelength.player.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResults(
    val songs: List<SongEntity> = emptyList(),
    val albums: List<com.wavelength.player.data.library.Album> = emptyList(),
    val artists: List<com.wavelength.player.data.library.Artist> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WavelengthApp).library

    val library: StateFlow<Library?> = repo.library
    val isScanning: StateFlow<Boolean> = repo.isScanning

    val player = PlayerConnection(app).also { it.connect() }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<SearchResults> =
        combine(_query.debounce(150), repo.library) { q, lib -> search(q.trim(), lib) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) { _query.value = q }

    fun onPermissionGranted() = repo.startWatching()

    fun rescan() { viewModelScope.launch { repo.rescan() } }

    private fun search(q: String, lib: Library?): SearchResults {
        if (q.isEmpty() || lib == null) return SearchResults()
        fun String?.hit() = this?.contains(q, ignoreCase = true) == true
        return SearchResults(
            songs = lib.songs.filter { it.title.hit() || it.artist.hit() || it.album.hit() }.take(100),
            albums = lib.albums.filter { it.title.hit() || it.artist.hit() }.take(20),
            artists = lib.artists.filter { it.name.hit() }.take(20),
        )
    }

    override fun onCleared() {
        player.release()
    }
}
