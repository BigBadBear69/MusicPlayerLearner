package com.wavelength.player.data.db

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A song on the device. [id] is the MediaStore audio id, so it stays stable across rescans. */
@Entity(
    tableName = "songs",
    indices = [Index("albumId"), Index("artist"), Index("folder"), Index("genre")],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val genre: String?,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    /** Folder path relative to storage root, e.g. "Music/Rock/". */
    val folder: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}
