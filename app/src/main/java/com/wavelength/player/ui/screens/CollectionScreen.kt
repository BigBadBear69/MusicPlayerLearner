@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.ui.components.AlbumArt
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.PlayShuffleBar
import com.wavelength.player.ui.components.SongRow
import com.wavelength.player.ui.formatDuration
import com.wavelength.player.ui.songCount

/** A list of songs with a header: used for an album, artist, folder or genre. */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    songs: List<SongEntity>,
    cover: AlbumArt?,
    numbered: Boolean,
    currentSongId: Long?,
    onPlay: (List<SongEntity>, Int, Boolean) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (cover != null) {
                        ArtImage(cover, Modifier.size(200.dp), RoundedCornerShape(16.dp), iconSize = 56.dp)
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "$subtitle${if (subtitle.isNotEmpty()) " · " else ""}${songCount(songs.size)} · ${formatDuration(songs.sumOf { it.durationMs })}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                PlayShuffleBar(onPlay = { onPlay(songs, 0, false) }, onShuffle = { onPlay(songs, 0, true) })
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s,
                    s.id == currentSongId,
                    onClick = { onPlay(songs, i, false) },
                    leadingNumber = if (numbered) (s.trackNumber.takeIf { it > 0 } ?: (i + 1)) else null,
                )
            }
        }
    }
}
