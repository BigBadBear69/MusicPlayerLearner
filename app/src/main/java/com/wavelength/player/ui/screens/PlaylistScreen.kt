@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.ui.PlaylistEntry
import com.wavelength.player.ui.PlaylistUi
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.ConfirmDialog
import com.wavelength.player.ui.components.PlayShuffleBar
import com.wavelength.player.ui.components.PlaylistNameDialog
import com.wavelength.player.ui.components.SongMenu
import com.wavelength.player.ui.components.SongRow
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.formatDuration
import com.wavelength.player.ui.songCount
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistScreen(
    playlist: PlaylistUi,
    currentSongId: Long?,
    onPlay: (List<SongEntity>, Int?, Boolean) -> Unit,
    onMore: (SongMenu) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    // Local copy so dragging feels instant; it resets whenever the saved playlist changes.
    var rows by remember(playlist.entries) { mutableStateOf(playlist.entries) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = rows.indexOfFirst { it.position == from.key }
        val toIdx = rows.indexOfFirst { it.position == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            rows = rows.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val songs = rows.map { it.song }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Playlist options") }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Add all to…") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                            enabled = songs.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                onMore(SongMenu(songs, playlist.name, songCount(songs.size), songs.firstOrNull()?.art()))
                            },
                        )
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menuOpen = false; renaming = true })
                        DropdownMenuItem(text = { Text("Export as .m3u") }, leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                            enabled = songs.isNotEmpty(), onClick = { menuOpen = false; onExport() })
                        DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menuOpen = false; confirmDelete = true })
                    }
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtImage(songs.firstOrNull()?.art(), Modifier.size(180.dp), RoundedCornerShape(16.dp), iconSize = 56.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "${songCount(songs.size)} · ${formatDuration(songs.sumOf { it.durationMs })}" +
                            if (playlist.missingIds.isNotEmpty()) " · ${playlist.missingIds.size} missing" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (songs.isNotEmpty()) {
                    PlayShuffleBar(onPlay = { onPlay(songs, 0, false) }, onShuffle = { onPlay(songs, null, true) })
                } else {
                    Text(
                        "This playlist is empty. Tap ⋮ next to any song in your library and choose \"Add to playlist\".",
                        Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(rows, key = { it.position }) { entry ->
                ReorderableItem(reorderState, key = entry.position) { dragging ->
                    PlaylistRow(
                        entry, dragging, entry.song.id == currentSongId,
                        onClick = { onPlay(songs, rows.indexOf(entry), false) },
                        onMore = {
                            onMore(
                                SongMenu(
                                    listOf(entry.song), entry.song.title, entry.song.artist, entry.song.art(),
                                    playlist = playlist, playlistPosition = entry.position,
                                ),
                            )
                        },
                        handle = {
                            Icon(
                                Icons.Rounded.DragHandle, "Drag to reorder",
                                Modifier
                                    .draggableHandle(
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                        onDragStopped = { onReorder(rows.map { it.song.id }) },
                                    )
                                    .padding(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }

    if (renaming) PlaylistNameDialog("Rename playlist", playlist.name, "Rename", { renaming = false }, onRename)
    if (confirmDelete) {
        ConfirmDialog(
            "Delete \"${playlist.name}\"?",
            "The playlist is removed. Your songs stay on your phone.",
            "Delete",
            { confirmDelete = false },
            onDelete,
        )
    }
}

@Composable
private fun PlaylistRow(
    entry: PlaylistEntry,
    dragging: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    handle: @Composable () -> Unit,
) {
    Box(
        if (dragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)) else Modifier,
    ) {
        SongRow(entry.song, isCurrent, onClick = onClick, onMore = onMore, trailing = handle)
    }
}
