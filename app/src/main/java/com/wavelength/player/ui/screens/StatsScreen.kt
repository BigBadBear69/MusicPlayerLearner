@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wavelength.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavelength.player.data.db.Outcome
import com.wavelength.player.data.db.PlayEventEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.stats.GroupStat
import com.wavelength.player.data.stats.SongStat
import com.wavelength.player.data.stats.StatsRange
import com.wavelength.player.data.stats.StatsSnapshot
import com.wavelength.player.ui.components.ArtImage
import com.wavelength.player.ui.components.ColumnChart
import com.wavelength.player.ui.components.ConfirmDialog
import com.wavelength.player.ui.components.MagnitudeBar
import com.wavelength.player.ui.components.StatTile
import com.wavelength.player.ui.components.art
import com.wavelength.player.ui.formatDuration
import com.wavelength.player.ui.formatListenTime
import com.wavelength.player.ui.formatWhen
import com.wavelength.player.ui.percent
import com.wavelength.player.ui.theme.WavelengthColors
import kotlinx.coroutines.launch

private enum class StatsTab(val label: String) { Overview("Overview"), Songs("All songs"), History("History") }

@Composable
fun StatsScreen(
    snapshot: StatsSnapshot?,
    range: StatsRange,
    recent: List<PlayEventEntity>,
    songsById: Map<Long, SongEntity>,
    onRange: (StatsRange) -> Unit,
    onOpenSong: (SongEntity) -> Unit,
    onOpenArtist: (String) -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val pager = rememberPagerState { StatsTab.entries.size }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Your stats", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Stats options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export history (CSV)") },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                            onClick = { menu = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear listening history") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                            onClick = { menu = false; confirmReset = true },
                        )
                    }
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
            StatsTab.entries.forEachIndexed { i, t ->
                Tab(pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(t.label) })
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (StatsTab.entries[page]) {
                StatsTab.Overview -> Overview(snapshot, range, onRange, onOpenSong, onOpenArtist, contentPadding)
                StatsTab.Songs -> AllSongs(snapshot, onOpenSong, contentPadding)
                StatsTab.History -> History(recent, songsById, onOpenSong, contentPadding)
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            "Clear listening history?",
            "This deletes every play, skip and listen time, and resets each song's Affinity Score. Favourites and playlists are kept. This can't be undone. Export your history first if you want a copy.",
            "Clear",
            { confirmReset = false },
            onReset,
        )
    }
}

// ------------------------------------------------------------------ Overview

@Composable
private fun Overview(
    s: StatsSnapshot?,
    range: StatsRange,
    onRange: (StatsRange) -> Unit,
    onOpenSong: (SongEntity) -> Unit,
    onOpenArtist: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatsRange.entries) { r ->
                    FilterChip(
                        selected = r == range,
                        onClick = { onRange(r) },
                        label = { Text(r.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    )
                }
            }
        }
        if (s == null) return@LazyColumn
        if (s.isEmpty) item { EmptyStats() }

        item {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2,
            ) {
                val tile = Modifier.weight(1f)
                StatTile(formatListenTime(s.listenMs), "Listening time", tile)
                StatTile("%,d".format(s.plays), "Plays", tile)
                StatTile("%,d".format(s.skips), "Skips · ${percent(s.skipRate)} of listens", tile)
                StatTile("%,d".format(s.partials), "Stopped partway", tile)
                StatTile("%,d".format(s.distinctSongs), "Different songs", tile)
                StatTile(percent(s.avgCompletion), "Avg. of song heard", tile)
                StatTile("${s.currentStreakDays} days", "Listening streak", tile)
                StatTile("%,d".format(s.neverPlayed), "Never played (of %,d)".format(s.librarySize), tile)
            }
        }

        if (!s.isEmpty) {
            item {
                Section("Listening time per ${s.bucketUnit}")
                ColumnChart(
                    values = s.buckets.map { it.listenMs / 60_000.0 },
                    labels = s.buckets.map { it.label },
                    formatValue = { v -> formatListenTime((v * 60_000).toLong()) },
                    formatAxis = ::axisLabel,
                    description = "Listening time per ${s.bucketUnit}",
                    labelEvery = when (s.buckets.size) { in 0..7 -> 1; in 8..12 -> 2; in 13..31 -> 7; else -> 6 },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Section("When you listen")
                val hourLabels = List(24) { h -> "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "am" else "pm"}" }
                ColumnChart(
                    values = s.hourly.map { it / 60_000.0 },
                    labels = hourLabels,
                    formatValue = { v -> formatListenTime((v * 60_000).toLong()) },
                    formatAxis = ::axisLabel,
                    description = "Listening time by hour of day",
                    labelEvery = 6,
                    height = 120.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        rankedSongs("Top songs", "Most plays ${range.phrase()}", s.topSongs, onOpenSong) { "${it.plays} plays · ${formatListenTime(it.listenMs)}" to it.plays.toFloat() }
        rankedGroups("Top artists", s.topArtists, onClick = { onOpenArtist(it.name) })
        rankedGroups("Top albums", s.topAlbums, onClick = null)
        rankedGroups("Top genres", s.topGenres, onClick = null)
        rankedSongs("Most skipped", "Songs you skip the most ${range.phrase()}", s.mostSkipped, onOpenSong) {
            "${it.skips} skips · ${percent(it.skipRate)} of listens" to it.skips.toFloat()
        }
        rankedSongs("On your wavelength", "Highest Affinity Score, all time", s.onYourWavelength, onOpenSong) {
            "Affinity ${it.affinity}" to (it.affinity ?: 0).toFloat()
        }
        rankedSongs("Forgotten favourites", "Songs you love but haven't played in 30+ days", s.forgottenFavorites, onOpenSong) {
            "Affinity ${it.affinity} · last played ${it.lastPlayedAt?.let(::formatWhen) ?: "never"}" to (it.affinity ?: 0).toFloat()
        }
        item { HowItWorks() }
    }
}

/** Axis tick for a value in minutes: "45 m", "1 h", "1.5 h". */
private fun axisLabel(v: Double): String = when {
    v >= 60 -> (v / 60).let { if (it % 1.0 == 0.0) "${it.toInt()} h" else "%.1f h".format(it) }
    else -> "${v.toInt()} m"
}

private fun StatsRange.phrase() = when (this) {
    StatsRange.Week -> "in the last 7 days"
    StatsRange.Month -> "in the last 30 days"
    StatsRange.Year -> "in the last 12 months"
    StatsRange.All -> "of all time"
}

@Composable
private fun Section(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun LazyListScope.rankedSongs(
    title: String,
    subtitle: String,
    list: List<SongStat>,
    onOpenSong: (SongEntity) -> Unit,
    value: (SongStat) -> Pair<String, Float>,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { Section(title, subtitle) }
    item(key = "l-$title") {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val top = value(list.first()).second.coerceAtLeast(1f)
        Column {
            list.take(if (expanded) list.size else 5).forEachIndexed { i, st ->
                val (text, v) = value(st)
                RankRow(i + 1, st.song.title, st.song.artist, text, v / top, { onOpenSong(st.song) }) {
                    ArtImage(st.song.art(), Modifier.size(44.dp))
                }
            }
            if (list.size > 5) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (expanded) "Show less" else "Show all ${list.size}")
                }
            }
        }
    }
}

private fun LazyListScope.rankedGroups(title: String, list: List<GroupStat>, onClick: ((GroupStat) -> Unit)?) {
    if (list.isEmpty()) return
    item(key = "h-$title") { Section(title, "By listening time") }
    item(key = "l-$title") {
        val top = list.first().listenMs.toFloat().coerceAtLeast(1f)
        Column {
            list.take(5).forEachIndexed { i, g ->
                RankRow(
                    i + 1, g.name, g.subtitle, "${formatListenTime(g.listenMs)} · ${g.plays} plays", g.listenMs / top,
                    onClick?.let { c -> { c(g) } },
                ) { ArtImage(g.cover.art(), Modifier.size(44.dp), if (title == "Top artists") CircleShape else RoundedCornerShape(8.dp)) }
            }
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String,
    value: String,
    fraction: Float,
    onClick: (() -> Unit)?,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", Modifier.width(26.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MagnitudeBar(fraction, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyStats() {
    Column(
        Modifier.fillMaxWidth().padding(16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Play some music and your stats will appear here. Wavelength records every song you play, skip or stop partway through.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HowItWorks() {
    Row(
        Modifier.fillMaxWidth().padding(16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("How Wavelength counts", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "• Play: you heard at least half the song, or 4 minutes.\n" +
                    "• Skip: you moved on within the first 30 seconds.\n" +
                    "• Stopped partway: anything in between.\n" +
                    "• Only time actually playing counts; paused time doesn't.\n" +
                    "• Affinity Score (0–100, 50 = neutral) goes up with full plays, replays and favourites, and down with skips. " +
                    "Older listens gradually count less, so it follows your taste as it changes.\n" +
                    "• All of this stays on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ All songs

private enum class SongSort(val label: String) {
    Plays("Most played"), Skips("Most skipped"), SkipRate("Skip rate"), Listen("Listen time"),
    Affinity("Affinity"), Recent("Recently played"), Never("Never played"),
}

@Composable
private fun AllSongs(s: StatsSnapshot?, onOpenSong: (SongEntity) -> Unit, contentPadding: PaddingValues) {
    var sort by rememberSaveable { mutableStateOf(SongSort.Plays) }
    val rows = remember(s, sort) {
        val all = s?.allSongs.orEmpty()
        when (sort) {
            SongSort.Plays -> all.filter { it.listens > 0 }.sortedWith(compareByDescending<SongStat> { it.plays }.thenByDescending { it.listenMs })
            SongSort.Skips -> all.filter { it.skips > 0 }.sortedByDescending { it.skips }
            SongSort.SkipRate -> all.filter { it.listens >= 3 }.sortedByDescending { it.skipRate }
            SongSort.Listen -> all.filter { it.listenMs > 0 }.sortedByDescending { it.listenMs }
            SongSort.Affinity -> all.filter { it.affinity != null }.sortedByDescending { it.affinity }
            SongSort.Recent -> all.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }
            SongSort.Never -> all.filter { it.listens == 0 }.sortedBy { it.song.title.lowercase() }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SongSort.entries) { o -> FilterChip(selected = o == sort, onClick = { sort = o }, label = { Text(o.label) }) }
            }
            Text(
                "${rows.size} songs · all time" + if (sort == SongSort.SkipRate) " · songs with 3+ listens" else "",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(rows, key = { it.song.id }) { st -> SongStatRow(st) { onOpenSong(st.song) } }
    }
}

@Composable
private fun SongStatRow(st: SongStat, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(st.song.art(), Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.song.title, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (st.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Favorite, "Favourite", Modifier.size(14.dp), tint = WavelengthColors.Pink)
                }
            }
            Text(st.song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${st.plays} plays · ${st.skips} skips · ${st.partials} partial · ${formatListenTime(st.listenMs)}" +
                    (st.lastPlayedAt?.let { " · ${formatWhen(it)}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        AffinityBadge(st.affinity)
    }
}

/** The 0–100 score in a small pill; "—" when the song has no data yet. */
@Composable
fun AffinityBadge(score: Int?, modifier: Modifier = Modifier) {
    Column(modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(score?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("affinity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ History

@Composable
private fun History(recent: List<PlayEventEntity>, songsById: Map<Long, SongEntity>, onOpenSong: (SongEntity) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (recent.isEmpty()) item { EmptyStats() }
        else item {
            Text(
                "Your last ${recent.size} listens",
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(recent, key = { it.id }) { e ->
            val song = songsById[e.songId]
            Row(
                Modifier.fillMaxWidth().then(if (song != null) Modifier.clickable { onOpenSong(song) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtImage(song?.art(), Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "Song no longer on this phone", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${song?.artist ?: ""}${if (song != null) " · " else ""}${formatWhen(e.startedAt)}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (e.replay) Icon(Icons.Rounded.Repeat, "Replay", Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                OutcomeChip(e)
            }
        }
    }
}

@Composable
fun OutcomeChip(e: PlayEventEntity) {
    val (label, dot) = when (e.outcome) {
        Outcome.PLAYED -> "Played ${percent(e.completion)}" to WavelengthColors.Cyan
        Outcome.SKIPPED -> "Skipped at ${formatDuration(e.listenedMs)}" to WavelengthColors.Pink
        else -> "Stopped at ${percent(e.completion)}" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.padding(start = 6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
