@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.wavelength.player.data.db.DiscoverItemEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.discover.DiscoverRepository
import com.wavelength.player.data.discover.DiscoverState
import com.wavelength.player.data.discover.Recommendation
import com.wavelength.player.ui.PreviewState
import com.wavelength.player.ui.formatWhen
import com.wavelength.player.ui.theme.WavelengthColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DiscoverActions(
    val refresh: () -> Unit,
    val togglePreview: (Recommendation) -> Unit,
    val stopPreview: () -> Unit,
    val thumbsUp: (Recommendation) -> Unit,
    val thumbsDown: (Recommendation) -> Unit,
    val toggleWishlist: (Recommendation) -> Unit,
    val removeFromWishlist: (String) -> Unit,
    val playLocal: (SongEntity) -> Unit,
    val openSettings: () -> Unit,
    val previewProgress: () -> Float,
)

@Composable
fun DiscoverScreen(
    state: DiscoverState,
    hasLastFmKey: Boolean,
    online: Boolean,
    items: Map<String, DiscoverItemEntity>,
    covers: Map<String, DiscoverRepository.DeezerInfo>,
    preview: PreviewState,
    wishlist: List<Pair<DiscoverItemEntity, SongEntity?>>,
    actions: DiscoverActions,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { actions.stopPreview() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Discover", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = actions.refresh, enabled = !state.loading && online) { Icon(Icons.Rounded.Refresh, "Find new songs") }
                IconButton(onClick = actions.openSettings) { Icon(Icons.Rounded.Settings, "Discover settings") }
            },
        )
        PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
            Tab(pager.currentPage == 0, onClick = { scope.launch { pager.animateScrollToPage(0) } }, text = { Text("For you") })
            Tab(pager.currentPage == 1, onClick = { scope.launch { pager.animateScrollToPage(1) } }, text = { Text("Wishlist (${wishlist.size})") })
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page == 0) ForYou(state, hasLastFmKey, online, items, covers, preview, actions, contentPadding)
            else Wishlist(wishlist, covers, actions, contentPadding)
        }
    }
}

@Composable
private fun ForYou(
    state: DiscoverState,
    hasLastFmKey: Boolean,
    online: Boolean,
    items: Map<String, DiscoverItemEntity>,
    covers: Map<String, DiscoverRepository.DeezerInfo>,
    preview: PreviewState,
    actions: DiscoverActions,
    contentPadding: PaddingValues,
) {
    val result = state.result
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (!online) item {
            Notice(Icons.Rounded.ErrorOutline, "Discover is switched off", "Turn on \"Discover uses the internet\" in Settings to get recommendations.", "Settings", actions.openSettings)
        } else if (!hasLastFmKey) item {
            Notice(
                Icons.Rounded.AutoAwesome, "Get better matches",
                "Right now Discover uses Deezer's related artists. Add a free Last.fm API key in Settings for song-by-song matches.",
                "Set up", actions.openSettings,
            )
        }
        if (state.loading) item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(state.progress ?: "Working…", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary)
            }
        }
        state.error?.let { err -> item { Notice(Icons.Rounded.ErrorOutline, "Couldn't update", err, "Try again", actions.refresh) } }
        if (state.needsMoreListening) item {
            Notice(
                Icons.Rounded.MusicNote, "Listen a little more first",
                "Discover learns from songs you've played all the way through or marked as favourites. Play at least 3 songs you like (or ♥ them), then tap refresh.",
                null, null,
            )
        }
        if (result == null && !state.loading && !state.needsMoreListening && state.error == null && online) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Find new music you'll like", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = actions.refresh) { Text("Find songs") }
            }
        }
        if (result != null) {
            item {
                Text(
                    "Based on ${result.seedsUsed} songs you love · ${result.source} · updated ${formatWhen(result.createdAt)}",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            section("Top picks for you", null, result.topPicks, items, covers, preview, actions)
            result.becauseYouLike.forEach { (artist, list) -> section("Because you like $artist", null, list, items, covers, preview, actions) }
            section("New artists for you", "Artists that aren't in your library yet", result.newArtists, items, covers, preview, actions)
            if (result.isEmpty) item {
                Notice(Icons.Rounded.MusicNote, "No new suggestions", "Everything we found is already on your phone. Listen to more variety, then refresh.", null, null)
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    subtitle: String?,
    list: List<Recommendation>,
    items: Map<String, DiscoverItemEntity>,
    covers: Map<String, DiscoverRepository.DeezerInfo>,
    preview: PreviewState,
    actions: DiscoverActions,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    items(list, key = { "$title/${it.key}" }) { rec -> RecRow(rec, items[rec.key], covers[rec.key], preview, actions) }
}

@Composable
private fun RecRow(
    rec: Recommendation,
    item: DiscoverItemEntity?,
    info: DiscoverRepository.DeezerInfo?,
    preview: PreviewState,
    actions: DiscoverActions,
) {
    val isThis = preview.key == rec.key
    val failed = preview.failed == rec.key
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                UrlArt(info?.track?.coverUrl ?: item?.coverUrl, 56.dp, showIcon = false)
                // Big preview button on the cover.
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f))
                        .clickable { actions.togglePreview(rec) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isThis && preview.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        isThis && preview.playing -> {
                            PreviewRing(actions.previewProgress)
                            Icon(Icons.Rounded.Pause, "Stop preview", tint = Color.White)
                        }
                        else -> Icon(Icons.Rounded.PlayArrow, "Play 30-second preview", tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(rec.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        failed -> "No preview available, try \"Listen on…\""
                        rec.seedCount > 1 -> "Matches ${rec.seedCount} songs you love"
                        else -> "Because you like ${rec.becauseOf}"
                    },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Row(Modifier.padding(start = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            val liked = item?.verdict == 1
            IconButton(onClick = { actions.thumbsUp(rec) }) {
                Icon(if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, if (liked) "Liked" else "Like: more like this",
                    tint = if (liked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { actions.thumbsDown(rec) }) {
                Icon(if (item?.verdict == -1) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, "Not for me: hide",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val saved = item?.wishlisted == true
            IconButton(onClick = { actions.toggleWishlist(rec) }) {
                Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "On your Wishlist" else "Save to Wishlist",
                    tint = if (saved) WavelengthColors.Pink else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            ListenOn(rec.artist, rec.title, info?.track?.link ?: item?.link)
        }
    }
}

/** Thin ring showing how far into the 30-second preview we are. */
@Composable
private fun PreviewRing(progress: () -> Float) {
    var p by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { p = progress(); delay(200) } }
    CircularProgressIndicator(progress = { p }, modifier = Modifier.size(40.dp), strokeWidth = 2.dp, color = WavelengthColors.Cyan, trackColor = Color.White.copy(alpha = 0.25f))
}

/** "Listen on…" menu: opens the full song in another app. */
@Composable
private fun ListenOn(artist: String, title: String, deezerLink: String?) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val q = Uri.encode("$artist $title")
    fun go(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { }
    }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Listen on…")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("YouTube Music") }, onClick = { open = false; go("https://music.youtube.com/search?q=$q") })
            DropdownMenuItem(text = { Text("Spotify") }, onClick = { open = false; go("https://open.spotify.com/search/$q") })
            DropdownMenuItem(text = { Text("YouTube") }, onClick = { open = false; go("https://www.youtube.com/results?search_query=$q") })
            deezerLink?.let { link -> DropdownMenuItem(text = { Text("Deezer") }, onClick = { open = false; go(link) }) }
        }
    }
}

@Composable
private fun Wishlist(
    list: List<Pair<DiscoverItemEntity, SongEntity?>>,
    covers: Map<String, DiscoverRepository.DeezerInfo>,
    actions: DiscoverActions,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (list.isEmpty()) item {
            Notice(Icons.Rounded.BookmarkBorder, "Your Wishlist is empty",
                "Tap the bookmark on any recommendation to save it here. When a saved song shows up on your phone, it's marked so you can play it.", null, null)
        }
        items(list, key = { it.first.key }) { (item, local) ->
            Row(
                Modifier.fillMaxWidth().then(if (local != null) Modifier.clickable { actions.playLocal(local) } else Modifier)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlArt(item.coverUrl ?: covers[item.key]?.track?.coverUrl, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (local != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(4.dp))
                            Text("On your phone now: tap to play", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                if (local == null) ListenOn(item.artist, item.title, item.link ?: covers[item.key]?.track?.link)
                IconButton(onClick = { actions.removeFromWishlist(item.key) }) { Icon(Icons.Rounded.Close, "Remove from Wishlist", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun UrlArt(url: String?, size: Dp, showIcon: Boolean = true) {
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier.size(size).background(Brush.linearGradient(listOf(WavelengthColors.Violet.copy(alpha = 0.55f), WavelengthColors.Cyan.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) { if (showIcon) Icon(Icons.Rounded.MusicNote, null, tint = Color.White) }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp))) {
        if (url == null) placeholder()
        else SubcomposeAsyncImage(url, null, Modifier.size(size), contentScale = ContentScale.Crop, loading = { placeholder() }, error = { placeholder() })
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, action: String?, onAction: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

