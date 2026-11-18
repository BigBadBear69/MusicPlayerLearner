package com.wavelength.player.ui.components

import android.content.ContentResolver
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.wavelength.player.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Image request model: the cover art embedded in a song file (shared by the whole album). */
data class AlbumArt(val songUri: Uri, val albumId: Long)

fun SongEntity.art() = AlbumArt(uri, albumId)

/** Loads cover art through Android's thumbnail API, which reads the art embedded in the audio file. */
class AlbumArtFetcher(
    private val data: AlbumArt,
    private val resolver: ContentResolver,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val w = options.size.width.pxOrElse { 512 }.coerceIn(64, 1024)
        val h = options.size.height.pxOrElse { 512 }.coerceIn(64, 1024)
        val bitmap = resolver.loadThumbnail(data.songUri, Size(w, h), null)
        ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<AlbumArt> {
        override fun create(data: AlbumArt, options: Options, imageLoader: ImageLoader): Fetcher =
            AlbumArtFetcher(data, resolver, options)
    }

    /** Lets every song of an album share one cached image. */
    class AlbumKeyer : Keyer<AlbumArt> {
        override fun key(data: AlbumArt, options: Options) = "album-art:${data.albumId}"
    }
}
