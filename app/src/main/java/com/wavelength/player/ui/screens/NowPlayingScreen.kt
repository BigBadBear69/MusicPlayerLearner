package com.wavelength.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.wavelength.player.playback.PlayerState
import com.wavelength.player.playback.SleepTimerState
import com.wavelength.player.ui.MainViewModel.LyricsUi
import com.wavelength.player.ui.components.AlbumArt
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.LyricsPanel
import com.wavelength.player.ui.components.sleepLabel
import com.wavelength.player.ui.formatDuration
import com.wavelength.player.ui.theme.WavelengthColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NowPlayingActions(
    val collapse: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Long) -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val openQueue: () -> Unit,
    val toggleFavorite: () -> Unit,
    val showStats: () -> Unit,
    val openSleepTimer: () -> Unit,
    val openSpeed: () -> Unit,
    val openEqualizer: () -> Unit,
    val retryLyrics: () -> Unit,
)

@Composable
fun NowPlayingScreen(
    state: PlayerState,
    art: AlbumArt?,
    positionMs: Long,
    isFavorite: Boolean,
    lyrics: LyricsUi,
    sleep: SleepTimerState,
    actions: NowPlayingActions,
) {
    // While the user drags the slider, show their drag position instead of the live position.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    // Background tinted with the main colour of the cover art.
    val accent = artColor(art)
    val tint by animateColorAsState(accent ?: WavelengthColors.Violet, tween(600), label = "tint")

    androidx.compose.material3.Surface(color = WavelengthColors.Ink, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), WavelengthColors.Ink, WavelengthColors.Ink))),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.collapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", Modifier.size(32.dp)) }
                Text(
                    "NOW PLAYING", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                IconButton(onClick = actions.openQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Sleep timer") }, leadingIcon = { Icon(Icons.Rounded.Bedtime, null) }, onClick = { menu = false; actions.openSleepTimer() })
                        DropdownMenuItem(text = { Text("Playback speed") }, leadingIcon = { Icon(Icons.Rounded.Speed, null) }, onClick = { menu = false; actions.openSpeed() })
                        DropdownMenuItem(text = { Text("Equalizer") }, leadingIcon = { Icon(Icons.Rounded.Equalizer, null) }, onClick = { menu = false; actions.openEqualizer() })
                        DropdownMenuItem(text = { Text("Song stats") }, leadingIcon = { Icon(Icons.Rounded.Insights, null) }, onClick = { menu = false; actions.showStats() })
                    }
                }
            }

            AnimatedContent(showLyrics, Modifier.weight(1f).fillMaxWidth(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "art-lyrics") { lyricsMode ->
                if (lyricsMode) {
                    LyricsPanel(lyrics, positionMs, onSeek = actions.seek, onRetry = actions.retryLyrics, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ArtImage(
                            art,
                            Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f)
                                .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = tint),
                            RoundedCornerShape(24.dp),
                            iconSize = 96.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.title, Modifier.basicMarquee(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        state.artist + if (state.album.isNotEmpty()) " · ${state.album}" else "",
                        Modifier.basicMarquee(), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                IconButton(onClick = actions.toggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isFavorite) "Remove from favourites" else "Add to favourites",
                        tint = if (isFavorite) WavelengthColors.Pink else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Slider(
                value = fraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = { dragFraction?.let { actions.seek((it * duration).toLong()) }; dragFraction = null },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.tertiary, inactiveTrackColor = MaterialTheme.colorScheme.outline),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration((fraction * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = actions.previous, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(40.dp)) }
                Box(Modifier.size(76.dp).clip(CircleShape).background(WavelengthColors.Brand), contentAlignment = Alignment.Center) {
                    IconButton(onClick = actions.playPause, Modifier.fillMaxSize()) {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play",
                            Modifier.size(44.dp), tint = Color.White)
                    }
                }
                IconButton(onClick = actions.next, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(40.dp)) }
                IconButton(onClick = actions.cycleRepeat) {
                    val (icon, label) = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne to "Repeat one"
                        Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat to "Repeat all"
                        else -> Icons.Rounded.Repeat to "Repeat off"
                    }
                    Icon(icon, label, tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Lyrics toggle + status chips (sleep timer, speed).
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                FilterChip(
                    selected = showLyrics, onClick = { showLyrics = !showLyrics },
                    label = { Text("Lyrics") }, leadingIcon = { Icon(Icons.Rounded.Lyrics, null, Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                )
                sleepLabel(sleep)?.let { label ->
                    AssistChip(onClick = actions.openSleepTimer, label = { Text(label) },
                        leadingIcon = { Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary) },
                        colors = AssistChipDefaults.assistChipColors())
                }
                if (state.speed != 1f) {
                    AssistChip(onClick = actions.openSpeed, label = { Text("${"%.2f".format(state.speed).trimEnd('0').trimEnd('.')}×") },
                        leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp)) })
                }
            }
        }
    }
    }
}

/** The most vivid colour in the cover art (or null while loading / without art). */
@Composable
private fun artColor(art: AlbumArt?): Color? {
    val context = LocalContext.current
    var color by remember(art) { mutableStateOf<Color?>(null) }
    LaunchedEffect(art) {
        if (art == null) return@LaunchedEffect
        val bitmap: Bitmap? = runCatching {
            SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(art).size(96).allowHardware(false).build(),
            ).image?.toBitmap()
        }.getOrNull()
        color = bitmap?.let {
            withContext(Dispatchers.Default) {
                val p = Palette.from(it).generate()
                (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch)?.rgb?.let(::Color)
            }
        }
    }
    return color
}
