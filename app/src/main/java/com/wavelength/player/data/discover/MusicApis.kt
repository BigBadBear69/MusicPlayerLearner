package com.wavelength.player.data.discover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SimilarTrack(val artist: String, val title: String, val match: Double)
data class SimilarArtist(val name: String, val match: Double)

/** Thrown when Last.fm rejects the API key, so the UI can say exactly that. */
class InvalidApiKeyException : IOException("Last.fm didn't accept the API key")

/** Small JSON-over-HTTPS helper with a polite request rate and a persistent response cache. */
class JsonHttp(
    private val cache: suspend (String) -> String?,
    private val store: suspend (String, String) -> Unit,
    private val minIntervalMs: Long,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gate = Mutex()
    private var last = 0L

    /** GET [url] and return the body; served from cache when [cacheKey] is fresh. */
    suspend fun get(url: HttpUrl, cacheKey: String?): String {
        cacheKey?.let { k -> cache(k)?.let { return it } }
        val body = gate.withLock {
            val wait = last + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            last = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).header("User-Agent", "Wavelength-Android/1.0").build()).execute().use { r ->
                    val text = r.body.string()
                    if (!r.isSuccessful && r.code != 400 && r.code != 403) throw IOException("HTTP ${r.code}")
                    text
                }
            }
        }
        cacheKey?.let { store(it, body) }
        return body
    }
}

/** Last.fm: "people who listen to X also listen to Y", from millions of listeners. Needs a free API key. */
class LastFmApi(private val http: JsonHttp, private val apiKey: String) {
    private fun url(method: String, vararg params: Pair<String, String>): HttpUrl =
        "https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder().apply {
            addQueryParameter("method", method)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("api_key", apiKey)
            addQueryParameter("format", "json")
            addQueryParameter("autocorrect", "1")
        }.build()

    private fun parse(body: String): JSONObject {
        val o = JSONObject(body)
        if (o.has("error")) {
            val code = o.optInt("error")
            if (code == 10 || code == 26) throw InvalidApiKeyException()
            if (code == 6) return JSONObject() // track/artist not found: just no results
            throw IOException("Last.fm error $code: ${o.optString("message")}")
        }
        return o
    }

    private fun JSONObject.arrayOrSingle(name: String): JSONArray? =
        optJSONArray(name) ?: optJSONObject(name)?.let { JSONArray().put(it) }

    suspend fun similarTracks(artist: String, title: String, limit: Int = 30): List<SimilarTrack> {
        val body = http.get(url("track.getsimilar", "artist" to artist, "track" to title, "limit" to "$limit"), "lfm:st:${Normalize.key(artist, title)}:$limit")
        val arr = parse(body).optJSONObject("similartracks")?.arrayOrSingle("track") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { t ->
            val a = t.optJSONObject("artist")?.optString("name").orEmpty()
            val n = t.optString("name")
            if (a.isBlank() || n.isBlank()) null else SimilarTrack(a, n, t.optString("match").toDoubleOrNull() ?: 0.0)
        }
    }

    suspend fun similarArtists(artist: String, limit: Int = 15): List<SimilarArtist> {
        val body = http.get(url("artist.getsimilar", "artist" to artist, "limit" to "$limit"), "lfm:sa:${Normalize.artist(artist)}:$limit")
        val arr = parse(body).optJSONObject("similarartists")?.arrayOrSingle("artist") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }.mapNotNull { a ->
            a.optString("name").takeIf { it.isNotBlank() }?.let { SimilarArtist(it, a.optString("match").toDoubleOrNull() ?: 0.0) }
        }
    }

    suspend fun topTracks(artist: String, limit: Int = 5): List<String> {
        val body = http.get(url("artist.gettoptracks", "artist" to artist, "limit" to "$limit"), "lfm:tt:${Normalize.artist(artist)}:$limit")
        val arr = parse(body).optJSONObject("toptracks")?.arrayOrSingle("track") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
    }

    /** Cheap call used by Settings → "Test key". */
    suspend fun check() {
        parse(http.get(url("artist.getsimilar", "artist" to "Radiohead", "limit" to "1"), cacheKey = null))
    }
}

/** What Deezer knows about one song: a 30-second preview, cover art and a link. */
data class DeezerTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val previewUrl: String?,
    val coverUrl: String?,
    val link: String,
)

/** Deezer's public API: previews, covers, and related artists (used when there's no Last.fm key). No key needed. */
class DeezerApi(private val http: JsonHttp) {
    private val base = "https://api.deezer.com".toHttpUrl()

    private fun track(o: JSONObject) = DeezerTrack(
        id = o.optLong("id"),
        title = o.optString("title"),
        artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
        previewUrl = o.optString("preview").takeIf { it.startsWith("http") },
        coverUrl = o.optJSONObject("album")?.optString("cover_medium")?.takeIf { it.startsWith("http") },
        link = o.optString("link"),
    )

    /** Finds a song. Preview links expire after a few hours, so this result is cached only briefly. */
    suspend fun findTrack(artist: String, title: String): DeezerTrack? {
        // Deezer's fielded search (artist:"…" track:"…") is unreliable, so search plainly and check the match ourselves.
        val url = base.newBuilder().addPathSegment("search").addQueryParameter("q", "$artist $title").addQueryParameter("limit", "10").build()
        val data = JSONObject(http.get(url, cacheKey = null)).optJSONArray("data") ?: return null
        val wanted = Normalize.key(artist, title)
        val tracks = List(data.length()) { track(data.getJSONObject(it)) }
        return tracks.firstOrNull { Normalize.key(it.artist, it.title) == wanted }
            ?: tracks.firstOrNull { Normalize.artist(it.artist) == Normalize.artist(artist) }
    }

    suspend fun artistId(name: String): Long? {
        val url = base.newBuilder().addPathSegment("search").addPathSegment("artist").addQueryParameter("q", name).addQueryParameter("limit", "1").build()
        val data = JSONObject(http.get(url, "dz:aid:${Normalize.artist(name)}")).optJSONArray("data") ?: return null
        return if (data.length() > 0) data.getJSONObject(0).optLong("id").takeIf { it > 0 } else null
    }

    suspend fun relatedArtists(id: Long, limit: Int = 12): List<SimilarArtist> {
        val url = base.newBuilder().addPathSegments("artist/$id/related").addQueryParameter("limit", "$limit").build()
        val data = JSONObject(http.get(url, "dz:rel:$id:$limit")).optJSONArray("data") ?: return emptyList()
        val n = data.length()
        // Deezer lists related artists best-first without a score; turn the order into one.
        return List(n) { i -> SimilarArtist(data.getJSONObject(i).optString("name"), 1.0 - i.toDouble() / (n + 1)) }
    }

    suspend fun topTracks(id: Long, limit: Int = 5): List<String> {
        val url = base.newBuilder().addPathSegments("artist/$id/top").addQueryParameter("limit", "$limit").build()
        val data = JSONObject(http.get(url, "dz:top:$id:$limit")).optJSONArray("data") ?: return emptyList()
        return List(data.length()) { data.getJSONObject(it).optString("title") }.filter { it.isNotBlank() }
    }
}
