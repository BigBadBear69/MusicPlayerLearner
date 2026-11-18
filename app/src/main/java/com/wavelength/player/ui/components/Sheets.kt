@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.ui.PlaylistUi
import com.wavelength.player.ui.songCount

/** What a "⋮" menu acts on: one song, or a whole album/artist/folder/playlist. */
data class SongMenu(
    val songs: List<SongEntity>,
    val title: String,
    val subtitle: String,
    val art: AlbumArt?,
    /** Set when opened from inside a playlist, to offer "Remove from playlist". */
    val playlist: PlaylistUi? = null,
    val playlistPosition: Int? = null,
)

@Composable
fun SongActionsSheet(
    menu: SongMenu,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToAlbum: (() -> Unit)?,
    onGoToArtist: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onShowStats: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(menu.art, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(menu.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(menu.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { onPlayNext(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { onAddToQueue(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist…") { onAddToPlaylist() }
            onToggleFavorite?.let { toggle ->
                SheetAction(
                    if (isFavorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (isFavorite == true) "Remove from favourites" else "Add to favourites",
                ) { toggle(); onDismiss() }
            }
            onShowStats?.let { show -> SheetAction(Icons.Rounded.Insights, "Song stats") { onDismiss(); show() } }
            onGoToAlbum?.let { go -> SheetAction(Icons.Rounded.Album, "Go to album") { onDismiss(); go() } }
            onGoToArtist?.let { go -> SheetAction(Icons.Rounded.Person, "Go to artist") { onDismiss(); go() } }
            onRemoveFromPlaylist?.let { remove ->
                SheetAction(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") { remove(); onDismiss() }
            }
        }
    }
}

@Composable
fun AddToPlaylistSheet(
    songs: List<SongEntity>,
    playlists: List<PlaylistUi>,
    onDismiss: () -> Unit,
    onPick: (PlaylistUi) -> Unit,
    onNew: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Add ${if (songs.size == 1) "\"${songs[0].title}\"" else songCount(songs.size)} to…",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SheetAction(Icons.Rounded.Add, "New playlist", emphasized = true) { onNew() }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(playlists, key = { it.id }) { p ->
                    ListRow(p.name, songCount(p.entries.size), onClick = { onPick(p); onDismiss() }) {
                        ArtImage(p.songs.firstOrNull()?.art(), Modifier.size(44.dp))
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, emphasized: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (emphasized) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Asks for a playlist name (used for both "New playlist" and "Rename"). */
@Composable
fun PlaylistNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text.trim()); onDismiss() }, enabled = value.text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.secondary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
