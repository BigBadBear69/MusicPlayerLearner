package com.wavelength.player.data.library

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import com.wavelength.player.data.db.SongEntity

/** Reads every music file Android's MediaStore knows about. */
class MediaScanner(private val resolver: ContentResolver) {

    fun scan(): List<SongEntity> {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.ALBUM_ARTIST)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.RELATIVE_PATH)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()

        // Music only, and nothing shorter than MIN_DURATION_MS (skips ringtones, voice notes, UI sounds).
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())

        val songs = ArrayList<SongEntity>()
        resolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection, selection, args, null,
        )?.use { c ->
            fun col(name: String) = c.getColumnIndexOrThrow(name)
            val id = col(MediaStore.Audio.Media._ID)
            val title = col(MediaStore.Audio.Media.TITLE)
            val artist = col(MediaStore.Audio.Media.ARTIST)
            val album = col(MediaStore.Audio.Media.ALBUM)
            val albumId = col(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtist = col(MediaStore.Audio.Media.ALBUM_ARTIST)
            val duration = col(MediaStore.Audio.Media.DURATION)
            val track = col(MediaStore.Audio.Media.TRACK)
            val year = col(MediaStore.Audio.Media.YEAR)
            val path = col(MediaStore.Audio.Media.RELATIVE_PATH)
            val name = col(MediaStore.Audio.Media.DISPLAY_NAME)
            val mime = col(MediaStore.Audio.Media.MIME_TYPE)
            val size = col(MediaStore.Audio.Media.SIZE)
            val added = col(MediaStore.Audio.Media.DATE_ADDED)
            val modified = col(MediaStore.Audio.Media.DATE_MODIFIED)
            val genre = c.getColumnIndex("genre")

            while (c.moveToNext()) {
                val fileName = c.getString(name).orEmpty()
                songs += SongEntity(
                    id = c.getLong(id),
                    title = c.getString(title).cleanTag() ?: fileName.substringBeforeLast('.'),
                    artist = c.getString(artist).cleanTag() ?: UNKNOWN_ARTIST,
                    album = c.getString(album).cleanTag() ?: UNKNOWN_ALBUM,
                    albumId = c.getLong(albumId),
                    albumArtist = c.getString(albumArtist).cleanTag(),
                    genre = if (genre >= 0) c.getString(genre).cleanTag() else null,
                    durationMs = c.getLong(duration),
                    // MediaStore encodes disc number in the thousands: 2005 = disc 2, track 5.
                    trackNumber = c.getInt(track) % 1000,
                    year = c.getInt(year),
                    folder = c.getString(path).orEmpty(),
                    fileName = fileName,
                    mimeType = c.getString(mime),
                    sizeBytes = c.getLong(size),
                    dateAddedSec = c.getLong(added),
                    dateModifiedSec = c.getLong(modified),
                )
            }
        }
        return songs
    }

    private fun String?.cleanTag(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }

    companion object {
        const val MIN_DURATION_MS = 20_000L
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}
