package com.wavelength.player.data.library

import com.wavelength.player.data.db.SongEntity

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<SongEntity>,
) {
    /** Any song from the album works as the source of the album's cover art. */
    val coverSong: SongEntity get() = songs.first()
}

data class Artist(val name: String, val songs: List<SongEntity>, val albumCount: Int)

data class Folder(val path: String, val songs: List<SongEntity>) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Internal storage" }
}

data class Genre(val name: String, val songs: List<SongEntity>)

/** The whole library, grouped every way the browse tabs need. */
data class Library(
    val songs: List<SongEntity> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val genres: List<Genre> = emptyList(),
) {
    companion object {
        fun from(songs: List<SongEntity>): Library {
            val albumOrder = compareBy<SongEntity>({ it.trackNumber }, { it.title.lowercase() })
            val albums = songs.groupBy { it.albumId }.map { (id, s) ->
                val sorted = s.sortedWith(albumOrder)
                Album(
                    id = id,
                    title = sorted.first().album,
                    artist = sorted.first().albumArtist ?: sorted.groupingBy { it.artist }.eachCount()
                        .maxBy { it.value }.key,
                    year = sorted.maxOf { it.year },
                    songs = sorted,
                )
            }.sortedBy { it.title.lowercase() }

            val artists = songs.groupBy { it.artist }.map { (name, s) ->
                Artist(name, s.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber })),
                    s.distinctBy { it.albumId }.size)
            }.sortedBy { it.name.lowercase() }

            val folders = songs.groupBy { it.folder }.map { (path, s) ->
                Folder(path, s.sortedBy { it.fileName.lowercase() })
            }.sortedBy { it.path.lowercase() }

            val genres = songs.filter { it.genre != null }.groupBy { it.genre!! }.map { (name, s) ->
                Genre(name, s.sortedBy { it.title.lowercase() })
            }.sortedBy { it.name.lowercase() }

            return Library(songs, albums, artists, folders, genres)
        }
    }
}
