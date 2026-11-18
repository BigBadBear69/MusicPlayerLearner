@file:OptIn(ExperimentalMaterial3Api::class)

package com.wavelength.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavelength.player.data.lyrics.Lrc
import com.wavelength.player.data.lyrics.Lyrics
import com.wavelength.player.playback.SleepTimerState
import com.wavelength.player.ui.MainViewModel.LyricsUi
import com.wavelength.player.ui.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Lyrics in the Now Playing screen. Synced lyrics follow the song; tap a line to jump there. */
@Composable
fun LyricsPanel(lyrics: LyricsUi, positionMs: Long, onSeek: (Long) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (lyrics) {
            LyricsUi.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            LyricsUi.None -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("No lyrics found", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wavelength looks inside the song file, in your lyrics folder (Settings), and on LRCLIB.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text("Search again") }
            }
            is LyricsUi.Found -> when (val l = lyrics.lyrics) {
                is Lyrics.Synced -> SyncedLyrics(l, positionMs, onSeek)
                is Lyrics.Plain -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                    Text(l.text, style = MaterialTheme.typography.titleMedium, lineHeight = 30.sp)
                    Spacer(Modifier.height(16.dp))
                    Source(l.source)
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(l: Lyrics.Synced, positionMs: Long, onSeek: (Long) -> Unit) {
    val current = Lrc.currentIndex(l.lines, positionMs + 300) // show a line a moment before it's sung
    val list = rememberLazyListState()
    LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 24.dp)) {
        itemsIndexed(l.lines) { i, line ->
            val color by animateColorAsState(
                when {
                    i == current -> Color.White
                    i < current -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "lyric",
            )
            Text(
                line.text.ifBlank { "♪" },
                Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 30.sp),
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.SemiBold,
                color = color,
            )
        }
        item { Source(l.source) }
    }
}

@Composable
private fun Source(source: String) {
    Text("Lyrics: $source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Short label for the sleep timer chip ("23:10 left", "After this song"). */
@Composable
fun sleepLabel(state: SleepTimerState): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) { while (state.endsAt != null) { now = System.currentTimeMillis(); delay(1000) } }
    return when {
        state.endOfSong -> "After this song"
        state.endsAt != null -> "${formatDuration((state.endsAt - now).coerceAtLeast(0))} left"
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onMinutes: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Music fades out and pauses. Handy for falling asleep.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            sleepLabel(state)?.let { label ->
                Text("Pausing: $label", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.endsAt != null) {
                        OutlinedButton(onClick = { onExtend(5) }) { Text("+5 min") }
                        OutlinedButton(onClick = { onExtend(15) }) { Text("+15 min") }
                    }
                    OutlinedButton(onClick = { onCancel(); onDismiss() }) { Text("Turn off") }
                }
                Spacer(Modifier.height(16.dp))
                Text("Or start a new timer", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(5, 10, 15, 30, 45, 60, 90, 120).forEach { m ->
                    FilterChip(false, onClick = { onMinutes(m); onDismiss() }, label = { Text(if (m < 60) "$m min" else "${m / 60} h${if (m % 60 > 0) " ${m % 60}" else ""}") })
                }
                FilterChip(state.endOfSong, onClick = { onEndOfSong(); onDismiss() }, label = { Text("End of this song") })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(speed) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Playback speed: ${"%.2f".format(value).trimEnd('0').trimEnd('.')}×", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = { value = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { onSpeed(value) },
                valueRange = 0.5f..2f,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    FilterChip(value == s, onClick = { value = s; onSpeed(s) }, label = { Text("${s}×".replace(".0×", "×")) })
                }
            }
        }
    }
}
