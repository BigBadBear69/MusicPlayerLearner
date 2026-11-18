package com.wavelength.player.data.discover

import com.wavelength.player.data.db.ApiCacheEntity
import com.wavelength.player.data.db.DiscoverItemEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.db.WavelengthDatabase
import com.wavelength.player.data.settings.SettingsStore
import com.wavelength.player.data.stats.Affinity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DiscoverState(
    val result: DiscoverResult? = null,
    val loading: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    /** Not enough listening data yet to know what you like. */
    val needsMoreListening: Boolean = false,
)

class DiscoverRepository(
    private val db: WavelengthDatabase,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val dao = db.discoverDao()
    private val statsDao = db.statsDao()

    private val http = JsonHttp(
        cache = { key -> dao.cached(key, System.currentTimeMillis() - CACHE_DAYS * DAY)?.body },
        store = { key, body -> dao.putCache(ApiCacheEntity(key, body, System.currentTimeMillis())) },
        minIntervalMs = 220, // Last.fm asks for no more than ~5 requests a second
    )
    private val deezer = DeezerApi(JsonHttp({ null }, { _, _ -> }, minIntervalMs = 120))

    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    val items: StateFlow<Map<String, DiscoverItemEntity>> =
        dao.observeItems().map { list -> list.associateBy { it.key } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Deezer info (cover, 30 s preview, link) per recommendation key; null = not found on Deezer. */
    private val _deezer = MutableStateFlow<Map<String, DeezerInfo>>(emptyMap())
    val deezerInfo: StateFlow<Map<String, DeezerInfo>> = _deezer.asStateFlow()

    data class DeezerInfo(val track: DeezerTrack?, val resolvedAt: Long)

    private var refreshJob: Job? = null

    init {
        scope.launch {
            dao.cached(RESULT_KEY, 0)?.body?.let { runCatching { decode(it) }.getOrNull() }?.let { saved ->
                _state.update { it.copy(result = saved) }
                resolveCovers(saved)
            }
        }
    }

    val hasLastFmKey: Boolean get() = settings.settings.value.lastFmApiKey.isNotBlank()

    suspend fun testLastFmKey(key: String): Result<Unit> = runCatching { LastFmApi(http, key.trim()).check() }

    /** Rebuilds recommendations from your current listening data. */
    fun refresh(songs: List<SongEntity>) {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            _state.update { it.copy(loading = true, error = null, progress = "Looking at what you love…", needsMoreListening = false) }
            try {
                if (!settings.settings.value.discoverOnline) error("Discover is switched off in Settings")
                val result = build(songs)
                if (result == null) {
                    _state.update { it.copy(loading = false, progress = null, needsMoreListening = true) }
                    return@launch
                }
                dao.putCache(ApiCacheEntity(RESULT_KEY, encode(result), System.currentTimeMillis()))
                _state.update { it.copy(result = result, loading = false, progress = null) }
                resolveCovers(result)
            } catch (e: InvalidApiKeyException) {
                _state.update { it.copy(loading = false, progress = null, error = "Last.fm didn't accept your API key. Check it in Settings.") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, progress = null, error = e.message ?: "Couldn't reach the music services. Check your connection.") }
            }
        }
    }

    private suspend fun build(songs: List<SongEntity>): DiscoverResult? {
        val stats = statsDao.observeAllStats().first().associateBy { it.songId }
        val items = items.value
        val now = System.currentTimeMillis()
        val seeds = pickSeeds(songs, stats, items, now)
        if (seeds.size < MIN_SEEDS) return null

        val key = settings.settings.value.lastFmApiKey
        val libraryKeys = songs.mapTo(HashSet()) { Normalize.key(it.artist, it.title) }
        val taste = artistTaste(songs, stats, now)
        val disliked = items.values.filter { it.verdict < 0 }
        val reactions = items.values.associate { it.key to (1.0 + 0.1 * it.previewListens - 0.25 * it.previewSkips).coerceIn(0.3, 1.5) }

        val similarTracks: List<List<SimilarTrack>>
        val routes = ArrayList<DiscoverEngine.ArtistRoute>()
        val seedArtists = seeds.groupBy { Normalize.artist(it.artist) }
            .map { (_, s) -> s.first().artist to s.sumOf { it.weight }.coerceAtMost(1.0) }
            .sortedByDescending { it.second }.take(6)
        val source: String

        if (key.isNotBlank()) {
            source = "Last.fm"
            val lfm = LastFmApi(http, key)
            similarTracks = seeds.mapIndexed { i, s ->
                _state.update { it.copy(progress = "Finding songs like \"${s.title}\" (${i + 1}/${seeds.size})") }
                runCatching { lfm.similarTracks(s.artist, s.title) }.getOrElse { if (it is InvalidApiKeyException) throw it else emptyList() }
            }
            seedArtists.forEachIndexed { i, (artist, weight) ->
                _state.update { it.copy(progress = "Finding artists like $artist (${i + 1}/${seedArtists.size})") }
                val similar = runCatching { lfm.similarArtists(artist, 10) }.getOrDefault(emptyList())
                    .filter { Normalize.artist(it.name) !in taste }.take(3)
                similar.forEach { a ->
                    val top = runCatching { lfm.topTracks(a.name, 4) }.getOrDefault(emptyList())
                    routes += DiscoverEngine.ArtistRoute(artist, weight, a.name, a.match, top)
                }
            }
        } else {
            // No Last.fm key: Deezer's related artists still give good "new artist" suggestions.
            source = "Deezer"
            similarTracks = seeds.map { emptyList() }
            seedArtists.forEachIndexed { i, (artist, weight) ->
                _state.update { it.copy(progress = "Finding artists like $artist (${i + 1}/${seedArtists.size})") }
                val id = runCatching { deezer.artistId(artist) }.getOrNull() ?: return@forEachIndexed
                val related = runCatching { deezer.relatedArtists(id, 12) }.getOrDefault(emptyList())
                related.take(6).forEach { a ->
                    val aid = runCatching { deezer.artistId(a.name) }.getOrNull() ?: return@forEach
                    val top = runCatching { deezer.topTracks(aid, 4) }.getOrDefault(emptyList())
                    routes += DiscoverEngine.ArtistRoute(artist, weight, a.name, a.match, top)
                }
            }
        }

        _state.update { it.copy(progress = "Ranking…") }
        return DiscoverEngine.rank(
            DiscoverEngine.Input(
                seeds = seeds,
                similarTracks = similarTracks,
                artistRoutes = routes,
                libraryTrackKeys = libraryKeys,
                libraryArtistTaste = taste,
                dislikedKeys = disliked.mapTo(HashSet()) { it.key },
                dislikedArtists = disliked.groupingBy { Normalize.artist(it.artist) }.eachCount(),
                previewReaction = reactions,
            ),
            source,
        )
    }

    /** Your best-loved songs (high Affinity, favourites, thumbs-up in Discover) as starting points. */
    private fun pickSeeds(songs: List<SongEntity>, stats: Map<Long, SongStatsEntity>, items: Map<String, DiscoverItemEntity>, now: Long): List<Seed> {
        val scored = songs.mapNotNull { song ->
            val s = stats[song.id] ?: return@mapNotNull null
            if (s.listens == 0 && !s.favorite) return@mapNotNull null
            Triple(song, Affinity.score(s.affinityRaw, s.affinityUpdatedAt, s.favorite, now), s)
        }
        val loved = scored.filter { it.second >= 58 || it.third.favorite }.sortedByDescending { it.second }
        val fallback = scored.filter { it.third.plays > 0 && it.second >= 50 }.sortedByDescending { it.third.plays }
        val picked = (loved + fallback).distinctBy { it.first.id }
            // Keep variety: at most 3 seed songs per artist.
            .let { list -> val per = HashMap<String, Int>(); list.filter { per.merge(Normalize.artist(it.first.artist), 1, Int::plus)!! <= 3 } }
            .take(MAX_SEEDS)
            .map { (song, score, _) -> Seed(song.artist, song.title, ((score - 50) / 50.0).coerceIn(0.25, 1.0)) }
        val liked = items.values.filter { it.verdict > 0 }.sortedByDescending { it.updatedAt }.take(5)
            .map { Seed(it.artist, it.title, 0.7) }
        return (picked + liked).distinctBy { Normalize.key(it.artist, it.title) }
    }

    /** How much you like each artist in your library: average Affinity mapped to -1..1. */
    private fun artistTaste(songs: List<SongEntity>, stats: Map<Long, SongStatsEntity>, now: Long): Map<String, Double> =
        songs.groupBy { Normalize.artist(it.artist) }.mapValues { (_, list) ->
            val scores = list.mapNotNull { stats[it.id]?.takeIf { s -> s.listens > 0 } }
                .map { Affinity.score(it.affinityRaw, it.affinityUpdatedAt, it.favorite, now) }
            if (scores.isEmpty()) 0.0 else (scores.average() - 50) / 50
        }

    // ---------------------------------------------------------------- feedback

    private suspend fun upsert(rec: Recommendation, change: (DiscoverItemEntity) -> DiscoverItemEntity) {
        val now = System.currentTimeMillis()
        val info = _deezer.value[rec.key]?.track
        val old = dao.item(rec.key) ?: DiscoverItemEntity(rec.key, rec.artist, rec.title)
        dao.putItem(change(old).copy(updatedAt = now, coverUrl = old.coverUrl ?: info?.coverUrl, link = old.link ?: info?.link))
    }

    fun setVerdict(rec: Recommendation, verdict: Int) = scope.launch {
        upsert(rec) { it.copy(verdict = if (it.verdict == verdict) 0 else verdict) }
        if (verdict < 0) {
            // Hide it straight away.
            _state.update { st -> st.copy(result = st.result?.without(rec.key)) }
        }
    }

    fun toggleWishlist(rec: Recommendation) = scope.launch {
        upsert(rec) { it.copy(wishlisted = !it.wishlisted, wishlistedAt = if (it.wishlisted) null else System.currentTimeMillis()) }
    }

    fun removeFromWishlist(key: String) = scope.launch {
        dao.item(key)?.let { dao.putItem(it.copy(wishlisted = false, wishlistedAt = null, updatedAt = System.currentTimeMillis())) }
    }

    /** How long you listened to a preview teaches Discover a little, too. */
    fun recordPreview(rec: Recommendation, listenedMs: Long) = scope.launch {
        when {
            listenedMs < 5_000 -> upsert(rec) { it.copy(previewSkips = it.previewSkips + 1) }
            listenedMs >= 20_000 -> upsert(rec) { it.copy(previewListens = it.previewListens + 1) }
        }
    }

    // ---------------------------------------------------------------- Deezer previews & covers

    /** Finds the 30-second preview (re-fetched when older than 2 hours, since the links expire). */
    suspend fun preview(rec: Recommendation): DeezerTrack? {
        val cached = _deezer.value[rec.key]
        if (cached != null && System.currentTimeMillis() - cached.resolvedAt < 2 * HOUR) return cached.track
        return resolve(rec.key, rec.artist, rec.title)
    }

    private suspend fun resolve(key: String, artist: String, title: String): DeezerTrack? {
        val t = runCatching { deezer.findTrack(artist, title) }.getOrNull()
        _deezer.update { it + (key to DeezerInfo(t, System.currentTimeMillis())) }
        return t
    }

    private fun resolveCovers(result: DiscoverResult) = scope.launch {
        for (r in result.all) {
            if (_deezer.value[r.key] == null) resolve(r.key, r.artist, r.title)
        }
    }

    fun resolveWishlistCovers(list: List<DiscoverItemEntity>) = scope.launch {
        list.filter { it.coverUrl == null && _deezer.value[it.key] == null }.forEach { resolve(it.key, it.artist, it.title) }
    }

    suspend fun clearCache() {
        dao.clearCache()
        _deezer.value = emptyMap()
    }

    // ---------------------------------------------------------------- saving the last result

    private fun DiscoverResult.without(key: String) = copy(
        topPicks = topPicks.filter { it.key != key },
        becauseYouLike = becauseYouLike.map { (a, l) -> a to l.filter { it.key != key } },
        newArtists = newArtists.filter { it.key != key },
    )

    private fun encodeList(list: List<Recommendation>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("k", it.key).put("a", it.artist).put("t", it.title).put("s", it.score)
                .put("b", it.becauseOf).put("n", it.seedCount).put("new", it.newArtist))
        }
    }

    private fun decodeList(a: JSONArray) = List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Recommendation(o.getString("k"), o.getString("a"), o.getString("t"), o.getDouble("s"), o.getString("b"), o.getInt("n"), o.getBoolean("new"))
    }

    private fun encode(r: DiscoverResult) = JSONObject()
        .put("top", encodeList(r.topPicks))
        .put("because", JSONArray().apply { r.becauseYouLike.forEach { (a, l) -> put(JSONObject().put("artist", a).put("list", encodeList(l))) } })
        .put("new", encodeList(r.newArtists))
        .put("seeds", r.seedsUsed).put("source", r.source).put("at", r.createdAt)
        .toString()

    private fun decode(s: String): DiscoverResult {
        val o = JSONObject(s)
        val because = o.getJSONArray("because")
        return DiscoverResult(
            decodeList(o.getJSONArray("top")),
            List(because.length()) { because.getJSONObject(it).let { b -> b.getString("artist") to decodeList(b.getJSONArray("list")) } },
            decodeList(o.getJSONArray("new")),
            o.getInt("seeds"), o.getString("source"), o.getLong("at"),
        )
    }

    companion object {
        const val MIN_SEEDS = 3
        const val MAX_SEEDS = 15
        private const val CACHE_DAYS = 7
        private const val HOUR = 3_600_000L
        private const val DAY = 86_400_000L
        private const val RESULT_KEY = "discover:result"
    }
}

/** Shortcut used by the UI for counting thumbs etc. */
fun Flow<Map<String, DiscoverItemEntity>>.wishlist() = map { m -> m.values.filter { it.wishlisted }.sortedByDescending { it.wishlistedAt } }

