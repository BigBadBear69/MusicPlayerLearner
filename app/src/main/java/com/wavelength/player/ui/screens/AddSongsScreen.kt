@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.library.Library
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.formatDuration
import com.wavelength.player.ui.songCount

private enum class PickFilter(val label: String) { All("All songs"), NotAdded("Not in playlist"), Favorites("Favourites"), Recent("Recently added") }

/**
 * Pick many songs at once and add them to a playlist in one go.
 * Tick songs (or "Select all" for what's showing), search to narrow down, then "Add".
 */
@Composable
fun AddSongsScreen(
    playlistName: String,
    library: Library?,
    alreadyInPlaylist: Set<Long>,
    favoriteIds: Set<Long>,
    onAdd: (List<SongEntity>) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PickFilter.All) }
    // Keep selection order, so songs are added in the order you ticked them.
    val selected = remember { mutableStateListOf<Long>() }
    val songs = library?.songs.orEmpty()
    val byId = remember(songs) { songs.associateBy { it.id } }

    val shown = remember(songs, query, filter, alreadyInPlaylist, favoriteIds) {
        val q = query.trim()
        songs.asSequence()
            .filter { s ->
                when (filter) {
                    PickFilter.All -> true
                    PickFilter.NotAdded -> s.id !in alreadyInPlaylist
                    PickFilter.Favorites -> s.id in favoriteIds
                    PickFilter.Recent -> true
                }
            }
            .filter { s -> q.isEmpty() || s.title.contains(q, true) || s.artist.contains(q, true) || s.album.contains(q, true) }
            .let { seq -> if (filter == PickFilter.Recent) seq.sortedByDescending { it.dateAddedSec }.take(200) else seq }
            .toList()
    }
    val allShownSelected = shown.isNotEmpty() && shown.all { it.id in selected }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Add to $playlistName", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (selected.isEmpty()) "Tap songs to select them" else "${songCount(selected.size)} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel") } },
            actions = {
                TextButton(onClick = {
                    if (allShownSelected) selected.removeAll(shown.map { it.id }.toSet())
                    else shown.forEach { if (it.id !in selected) selected += it.id }
                }, enabled = shown.isNotEmpty()) { Text(if (allShownSelected) "Unselect all" else "Select all") }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PickFilter.entries) { f -> FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.label) }) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { song ->
                val checked = song.id in selected
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (checked) selected.remove(song.id) else selected += song.id }
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { if (it) selected += song.id else selected.remove(song.id) },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    )
                    ArtImage(song.art(), Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${song.artist} · ${formatDuration(song.durationMs)}",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (song.id in alreadyInPlaylist) {
                        Icon(Icons.Rounded.CheckCircle, "Already in playlist", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.isEmpty()) "Nothing selected" else songCount(selected.size),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                if (selected.isNotEmpty()) TextButton(onClick = { selected.clear() }) { Text("Clear") }
                Button(onClick = { onAdd(selected.mapNotNull { byId[it] }); onClose() }, enabled = selected.isNotEmpty()) {
                    Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}")
                }
            }
        }
    }
}
