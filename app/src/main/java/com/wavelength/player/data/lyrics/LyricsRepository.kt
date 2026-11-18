package com.wavelength.player.data.lyrics

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wavelength.player.data.db.LyricsDao
import com.wavelength.player.data.db.LyricsEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Finds lyrics, in this order:
 *  1. inside the audio file (ID3 / FLAC / M4A tags),
 *  2. a matching .lrc or .txt file in the lyrics folder you chose in Settings,
 *  3. LRCLIB (free, open lyrics database with time-synced lyrics), if online lyrics are on.
 * Results (including "nothing found") are cached; "nothing found" is retried after a week.
 */
class LyricsRepository(
    private val context: Context,
    private val dao: LyricsDao,
    private val settings: SettingsStore,
) {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()

    suspend fun lyrics(song: SongEntity, forceRefresh: Boolean = false): Lyrics? = withContext(Dispatchers.IO) {
        val cached = dao.get(song.id)
        val now = System.currentTimeMillis()
        if (cached != null && !forceRefresh && (cached.text != null || now - cached.fetchedAt < 7 * 86_400_000L)) {
            return@withContext Lrc.from(cached.text, cached.source)
        }
        val found = embedded(song) ?: folder(song) ?: online(song)
        dao.put(LyricsEntity(song.id, found?.first, found?.second ?: "none", now))
        found?.let { Lrc.from(it.first, it.second) }
    }

    private fun embedded(song: SongEntity): Pair<String, String>? =
        EmbeddedLyrics.read({ context.contentResolver.openInputStream(song.uri) }, song.mimeType, song.fileName)
            ?.let { it to "Embedded in the file" }

    private fun folder(song: SongEntity): Pair<String, String>? {
        val uri = settings.settings.value.lyricsFolderUri?.let(Uri::parse) ?: return null
        val dir = DocumentFile.fromTreeUri(context, uri) ?: return null
        val base = song.fileName.substringBeforeLast('.').lowercase()
        val candidates = setOf("$base.lrc", "$base.txt", "${song.artist} - ${song.title}.lrc".lowercase(), "${song.title}.lrc".lowercase())
        val file = runCatching { dir.listFiles().firstOrNull { it.name?.lowercase() in candidates } }.getOrNull() ?: return null
        val text = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: return null
        return text to "Lyrics folder (${file.name})"
    }

    private fun online(song: SongEntity): Pair<String, String>? {
        if (!settings.settings.value.lyricsOnline) return null
        fun get(url: okhttp3.HttpUrl): String? = runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "Wavelength-Android/1.0").build()).execute().use { r ->
                if (r.isSuccessful) r.body.string() else null
            }
        }.getOrNull()

        fun pick(o: JSONObject): String? =
            o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                ?: o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

        // Exact match first (artist + title + duration).
        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", song.artist)
            .addQueryParameter("track_name", song.title)
            .apply { if (song.album.isNotBlank()) addQueryParameter("album_name", song.album) }
            .addQueryParameter("duration", (song.durationMs / 1000).toString())
            .build()
        get(exact)?.let { body -> runCatching { pick(JSONObject(body)) }.getOrNull() }?.let { return it to "LRCLIB" }

        // Then a search, keeping only results whose length is within 5 seconds.
        val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", song.artist)
            .addQueryParameter("track_name", song.title)
            .build()
        val arr = get(search)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
        val best = List(arr.length()) { arr.getJSONObject(it) }
            .filter { abs(it.optDouble("duration", 0.0) * 1000 - song.durationMs) < 5_000 }
            .sortedByDescending { it.optString("syncedLyrics").isNotBlank() }
            .firstNotNullOfOrNull { pick(it) }
        return best?.let { it to "LRCLIB" }
    }
}
