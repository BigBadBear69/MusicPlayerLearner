package com.wavelength.player.data.library

import android.content.ContentResolver
import android.net.Uri
import com.wavelength.player.data.db.PlaylistDao
import com.wavelength.player.data.db.PlaylistSummary
import com.wavelength.player.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlaylistRepository(private val dao: PlaylistDao) {
    val playlists: Flow<List<PlaylistSummary>> = dao.observeSummaries()

    private fun now() = System.currentTimeMillis()

    suspend fun create(name: String, songIds: List<Long> = emptyList()): Long =
        dao.create(name.trim().ifEmpty { "New playlist" }, songIds, now())

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim(), now())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun add(id: Long, songIds: List<Long>) = dao.setSongs(id, dao.songIds(id) + songIds, now())

    suspend fun removeAt(id: Long, position: Int) {
        val ids = dao.songIds(id).toMutableList()
        if (position in ids.indices) ids.removeAt(position)
        dao.setSongs(id, ids, now())
    }

    suspend fun setOrder(id: Long, songIds: List<Long>) = dao.setSongs(id, songIds, now())

    /** Writes a standard .m3u8 playlist that other players can read. */
    suspend fun exportM3u(resolver: ContentResolver, target: Uri, songs: List<SongEntity>) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(target, "wt")?.bufferedWriter()?.use { w ->
            w.write("#EXTM3U\n")
            songs.forEach { s ->
                w.write("#EXTINF:${s.durationMs / 1000},${s.artist} - ${s.title}\n")
                w.write("/storage/emulated/0/${s.folder}${s.fileName}\n")
            }
        } ?: error("Couldn't write the file")
    }

    /**
     * Reads a .m3u/.m3u8 file and matches its entries to songs on this phone, by path first and then by
     * file name. Returns the new playlist's id and how many entries couldn't be found.
     */
    suspend fun importM3u(resolver: ContentResolver, source: Uri, name: String, library: List<SongEntity>): Pair<Long, Int> {
        val lines = withContext(Dispatchers.IO) {
            resolver.openInputStream(source)?.bufferedReader()?.use { it.readLines() } ?: error("Couldn't read the file")
        }
        val byPath = library.associateBy { (it.folder + it.fileName).lowercase() }
        val byName = library.groupBy { it.fileName.lowercase() }
        var missing = 0
        val ids = lines.map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { raw ->
                val path = Uri.decode(raw.removePrefix("file://")).replace('\\', '/')
                val lower = path.lowercase()
                val hit = byPath.entries.firstOrNull { lower.endsWith(it.key) }?.value
                    ?: byName[lower.substringAfterLast('/')]?.firstOrNull()
                if (hit == null) missing++
                hit?.id
            }
        return create(name, ids) to missing
    }
}
