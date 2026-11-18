@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.wavelength.player.data.db.PlayEventEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.stats.Affinity
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.MagnitudeBar
import com.wavelength.player.ui.components.StatTile
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.formatDate
import com.wavelength.player.ui.formatListenTime
import com.wavelength.player.ui.formatWhen
import com.wavelength.player.ui.percent
import com.wavelength.player.ui.theme.WavelengthColors

/** Everything Wavelength knows about one song. */
@Composable
fun SongStatsSheet(
    song: SongEntity,
    stats: SongStatsEntity?,
    events: List<PlayEventEntity>,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val favorite = stats?.favorite == true
    val listens = stats?.listens ?: 0
    val score = stats?.takeIf { it.listens > 0 || it.favorite }
        ?.let { Affinity.score(it.affinityRaw, it.affinityUpdatedAt, it.favorite, System.currentTimeMillis()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxHeight(0.9f).navigationBarsPadding()) {
            item {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(song.art(), Modifier.size(64.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} · ${song.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (favorite) "Remove from favourites" else "Add to favourites",
                            tint = if (favorite) WavelengthColors.Pink else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(score?.toString() ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.padding(bottom = 6.dp)) {
                            Text("Affinity Score", style = MaterialTheme.typography.labelLarge)
                            Text(affinityWords(score), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    MagnitudeBar((score ?: 0) / 100f)
                }
            }
            item {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 2,
                ) {
                    val m = Modifier.weight(1f)
                    StatTile("${stats?.plays ?: 0}", "Plays", m)
                    StatTile("${stats?.skips ?: 0}", "Skips", m)
                    StatTile("${stats?.partials ?: 0}", "Stopped partway", m)
                    StatTile("${stats?.replays ?: 0}", "Replays", m)
                    StatTile(formatListenTime(stats?.listenMs ?: 0), "Total listening", m)
                    StatTile(
                        if (listens > 0 && song.durationMs > 0) percent(((stats!!.listenMs.toDouble() / listens) / song.durationMs).coerceIn(0.0, 1.0).toFloat()) else "—",
                        "Avg. heard per listen", m,
                    )
                    StatTile(stats?.firstPlayedAt?.let(::formatDate) ?: "—", "First played", m)
                    StatTile(stats?.lastPlayedAt?.let(::formatDate) ?: "—", "Last played", m)
                }
                FilledTonalButton(onClick = { onPlay(); onDismiss() }, Modifier.fillMaxWidth().padding(20.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                Text(
                    if (events.isEmpty()) "No listens recorded yet." else "Listens",
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(events, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatWhen(e.startedAt), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(formatListenTime(e.listenedMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutcomeChip(e)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun affinityWords(score: Int?) = when {
    score == null -> "Not enough listening yet"
    score >= 80 -> "You love this one"
    score >= 60 -> "You like this"
    score > 40 -> "Neutral"
    score > 20 -> "Not really feeling it"
    else -> "You usually skip this"
}
