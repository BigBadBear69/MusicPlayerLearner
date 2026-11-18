@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.playback.QueueEntry
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.formatDuration

/** "Up next": the queue in the order it will actually play (shuffle-aware). */
@Composable
fun QueueSheet(
    upNext: List<QueueEntry>,
    queueSize: Int,
    shuffle: Boolean,
    songsById: Map<Long, SongEntity>,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "$queueSize in queue${if (shuffle) " · smart shuffle" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn {
                itemsIndexed(upNext, key = { _, e -> e.index }) { i, entry ->
                    val song = songsById[entry.songId] ?: return@itemsIndexed
                    val isCurrent = i == 0
                    Row(
                        Modifier.fillMaxWidth().clickable { onJump(entry.index) }.padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtImage(song.art(), Modifier.size(44.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                "${song.artist} · ${formatDuration(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isCurrent) {
                            Icon(Icons.Rounded.GraphicEq, "Playing now", Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                        } else {
                            IconButton(onClick = { onRemove(entry.index) }) {
                                Icon(Icons.Rounded.Close, "Remove from queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
