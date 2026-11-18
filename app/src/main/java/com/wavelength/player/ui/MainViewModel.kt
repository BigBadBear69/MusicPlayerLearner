package com.wavelength.player.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wavelength.player.WavelengthApp
import com.wavelength.player.data.db.PlayEventEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.stats.StatsRange
import com.wavelength.player.data.db.DiscoverItemEntity
import com.wavelength.player.data.discover.DiscoverState
import com.wavelength.player.data.discover.Normalize
import com.wavelength.player.data.discover.Recommendation
import com.wavelength.player.data.settings.AppSettings
import com.wavelength.player.data.stats.StatsSnapshot
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import com.wavelength.player.data.lyrics.Lyrics
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.wavelength.player.data.smart.ShuffleStyle
import com.wavelength.player.data.smart.SmartPlaylist
import com.wavelength.player.data.smart.SmartPlaylists
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
    private val statsRepo = (app as WavelengthApp).stats
    private val discoverRepo = (app as WavelengthApp).discover
    private val settingsStore = (app as WavelengthApp).settings
    private val wapp = app as WavelengthApp

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

    // ---- Stats & favourites ----

    private val _statsRange = MutableStateFlow(StatsRange.Month)
    val statsRange: StateFlow<StatsRange> = _statsRange.asStateFlow()
    fun setStatsRange(r: StatsRange) { _statsRange.value = r }

    val stats: StateFlow<StatsSnapshot?> =
        statsRepo.snapshot(_statsRange, repo.library.map { it?.songs.orEmpty() })
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentListens: StateFlow<List<PlayEventEntity>> =
        statsRepo.recent(300).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<Long>> =
        statsRepo.favoriteIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Per-song totals (all time), for the song details sheet. */
    val songStats: StateFlow<Map<Long, SongStatsEntity>> =
        statsRepo.allStats.map { list -> list.associateBy { it.songId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun eventsForSong(songId: Long) = statsRepo.eventsForSong(songId)

    fun toggleFavorite(song: SongEntity) {
        val fav = song.id !in favoriteIds.value
        viewModelScope.launch {
            statsRepo.setFavorite(song.id, fav)
            say(if (fav) "Added \"${song.title}\" to Favourites" else "Removed \"${song.title}\" from Favourites")
        }
    }

    fun exportStats(target: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            runCatching { statsRepo.exportCsv(resolver, target, library.value?.songs?.associateBy { it.id }.orEmpty()) }
                .onSuccess { say("Exported $it listens") }
                .onFailure { say("Export failed: ${it.message}") }
        }
    }

    fun resetStats() {
        viewModelScope.launch {
            statsRepo.resetHistory()
            say("Listening history cleared")
        }
    }

    // ---- Discover ----

    val discover: StateFlow<DiscoverState> = discoverRepo.state
    val discoverItems: StateFlow<Map<String, DiscoverItemEntity>> = discoverRepo.items
    val deezerInfo = discoverRepo.deezerInfo
    val settings: StateFlow<AppSettings> = settingsStore.settings

    /** Wishlist, each marked with the matching song if it's now on the phone. */
    val wishlist: StateFlow<List<Pair<DiscoverItemEntity, SongEntity?>>> =
        combine(discoverRepo.items, repo.library) { items, lib ->
            val byKey = lib?.songs?.associateBy { Normalize.key(it.artist, it.title) }.orEmpty()
            items.values.filter { it.wishlisted }.sortedByDescending { it.wishlistedAt }.map { it to byKey[it.key] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val preview = PreviewPlayer(app) { key, ms ->
        discoverRepo.state.value.result?.all?.find { it.key == key }?.let { discoverRepo.recordPreview(it, ms) }
    }
    val previewState: StateFlow<PreviewState> = preview.state
    val previewPositionMs get() = preview.positionMs
    val previewDurationMs get() = preview.durationMs

    fun refreshDiscover() {
        library.value?.songs?.let(discoverRepo::refresh)
    }

    /** Refresh automatically the first time, and when recommendations are more than 3 days old. */
    fun maybeAutoRefreshDiscover() {
        val st = discover.value
        val age = st.result?.let { System.currentTimeMillis() - it.createdAt }
        if (!st.loading && (st.result == null || age!! > 3 * 86_400_000L) && settings.value.discoverOnline) refreshDiscover()
    }

    fun togglePreview(rec: Recommendation) {
        val st = preview.state.value
        if (st.key == rec.key && (st.playing || st.loading)) { preview.stop(); return }
        preview.loading(rec.key)
        viewModelScope.launch {
            val track = discoverRepo.preview(rec)
            if (preview.state.value.key != rec.key) return@launch // user moved on
            val url = track?.previewUrl
            if (url == null) preview.notFound(rec.key) else preview.play(rec.key, url)
        }
    }

    fun stopPreview() = preview.stop()

    fun thumbsUp(rec: Recommendation) {
        val was = discoverItems.value[rec.key]?.verdict == 1
        discoverRepo.setVerdict(rec, 1)
        if (!was) say("Got it: more like \"${rec.title}\" next time")
    }

    fun thumbsDown(rec: Recommendation) {
        if (preview.state.value.key == rec.key) preview.stop()
        discoverRepo.setVerdict(rec, -1)
        say("Hidden. You'll see less like this")
    }

    fun toggleWishlist(rec: Recommendation) {
        val was = discoverItems.value[rec.key]?.wishlisted == true
        discoverRepo.toggleWishlist(rec)
        say(if (was) "Removed from Wishlist" else "Saved \"${rec.title}\" to your Wishlist")
    }

    fun removeFromWishlist(key: String) = discoverRepo.removeFromWishlist(key)

    fun resolveWishlistCovers(list: List<DiscoverItemEntity>) = discoverRepo.resolveWishlistCovers(list)

    // ---- Smart playlists & shuffle style ----

    val smartPlaylists: StateFlow<List<SmartPlaylist>> =
        combine(
            repo.library,
            statsRepo.allStats,
            statsRepo.eventsSince(System.currentTimeMillis() - 90 * 86_400_000L),
        ) { lib, stats, events ->
            if (lib == null) emptyList() else SmartPlaylists.build(lib.songs, stats.associateBy { it.songId }, events)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setShuffleStyle(style: ShuffleStyle) {
        settingsStore.update { it.copy(shuffleStyle = style) }
        if (player.state.value.shuffle) say("${style.label} shuffle: the rest of the queue was reshuffled")
    }

    fun setRepeatWindow(w: Double) = settingsStore.update { it.copy(repeatWindow = w.coerceIn(0.3, 0.9)) }

    // ---- Lyrics ----

    sealed interface LyricsUi {
        data object Loading : LyricsUi
        data object None : LyricsUi
        data class Found(val lyrics: Lyrics) : LyricsUi
    }

    private val lyricsRefresh = MutableStateFlow(0)

    /** Lyrics for whatever is playing now. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<LyricsUi> =
        combine(player.state.map { it.songId }.distinctUntilChanged(), lyricsRefresh) { id, n -> id to n }
            .flatMapLatest { (id, n) ->
                flow {
                    val song = id?.let { library.value?.songs?.find { s -> s.id == it } }
                    if (song == null) { emit(LyricsUi.None); return@flow }
                    emit(LyricsUi.Loading)
                    val l = runCatching { wapp.lyrics.lyrics(song, forceRefresh = n > 0) }.getOrNull()
                    emit(if (l == null) LyricsUi.None else LyricsUi.Found(l))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUi.None)

    fun retryLyrics() { lyricsRefresh.value++ }
    fun setLyricsOnline(on: Boolean) = settingsStore.update { it.copy(lyricsOnline = on) }
    fun setLyricsFolder(uri: Uri?) {
        uri?.let {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        settingsStore.update { it.copy(lyricsFolderUri = uri?.toString()) }
        say(if (uri == null) "Lyrics folder removed" else "Lyrics folder set")
    }

    // ---- Sleep timer, speed, equalizer ----

    val sleepTimer = wapp.sleepTimer.state
    fun sleepIn(minutes: Int) { wapp.sleepTimer.startMinutes(minutes); say("Sleep timer: pausing in $minutes min") }
    fun sleepAtEndOfSong() { wapp.sleepTimer.endOfSong(); say("Sleep timer: pausing after this song") }
    fun extendSleep(minutes: Int) = wapp.sleepTimer.extend(minutes)
    fun cancelSleep() { wapp.sleepTimer.cancel(); say("Sleep timer off") }

    fun setSpeed(speed: Float) = player.setSpeed(speed)

    val eqInfo = wapp.audioEffects.info
    val eqSettings = wapp.audioEffects.settings
    val eqSupported = wapp.audioEffects.supported
    fun setEqEnabled(on: Boolean) = wapp.audioEffects.update { it.copy(enabled = on) }
    fun setEqPreset(name: String) = wapp.audioEffects.choosePreset(name)
    fun setEqBand(index: Int, db: Float) = wapp.audioEffects.setBand(index, db)
    fun setBassBoost(v: Float) = wapp.audioEffects.update { it.copy(bassBoost = v, enabled = true) }

    // ---- Settings ----

    fun setLastFmKey(key: String) = settingsStore.update { it.copy(lastFmApiKey = key) }
    fun setDiscoverOnline(on: Boolean) = settingsStore.update { it.copy(discoverOnline = on) }
    suspend fun testLastFmKey(key: String) = discoverRepo.testLastFmKey(key)
    fun clearDiscoverCache() {
        viewModelScope.launch {
            discoverRepo.clearCache()
            say("Discover cache cleared")
        }
    }

    // ---- Playlists: add many songs at once ----

    fun addSongsToPlaylist(playlistId: Long, songs: List<SongEntity>) {
        val p = playlists.value.find { it.id == playlistId } ?: return
        if (songs.isNotEmpty()) addToPlaylist(p, songs)
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
        preview.release()
        player.release()
    }
}
