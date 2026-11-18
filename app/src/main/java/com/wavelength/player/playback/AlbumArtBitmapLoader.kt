package com.wavelength.player.playback

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Gives the media notification / lock screen the cover art. Each song's artworkUri is the song's own
 * content URI, and Android's thumbnail API pulls the art embedded in the file.
 */
@UnstableApi
class AlbumArtBitmapLoader(private val resolver: ContentResolver) : BitmapLoader {
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String) = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Could not decode artwork")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        resolver.loadThumbnail(uri, Size(512, 512), null)
    }
}
