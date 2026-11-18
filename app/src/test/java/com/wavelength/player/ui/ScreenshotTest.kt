package com.wavelength.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.wavelength.player.data.db.Outcome
import com.wavelength.player.data.db.PlayEventEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.db.EventRow
import com.wavelength.player.data.stats.StatsRange
import com.wavelength.player.data.stats.StatsRepository
import com.wavelength.player.ui.screens.SongStatsSheet
import com.wavelength.player.ui.screens.StatsScreen
import com.wavelength.player.ui.theme.WavelengthTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Renders screens with fake data and saves PNGs to build/screenshots so they can be looked at.
 * Only runs when -Pscreenshots is passed (it's slow).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h2200dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val outDir = File(System.getProperty("screenshotDir") ?: "build/screenshots")

    private fun save(name: String) {
        outDir.mkdirs()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val min = 60_000L
    private val artists = listOf("Nova Lane", "The Paper Kites", "Kavinsky", "Tycho", "Phoebe Bridgers", "Bonobo", "M83", "Khruangbin")
    private val songs = List(40) { i ->
        val a = artists[i % artists.size]
        SongEntity(i.toLong(), listOf("Midnight", "Glow", "Echoes", "Daydream", "Neon", "Drift", "Signal", "Horizon")[i % 8] + " ${i + 1}",
            a, "$a Album", (i % artists.size).toLong(), null, listOf("Electronic", "Indie", "Synthwave", "Ambient")[i % 4],
            (3 * min + i * 7_000), 1, 2020, "Music/", "s$i.mp3", null, 0, 0, 0)
    }

    private fun fakeData(): Pair<List<EventRow>, List<SongStatsEntity>> {
        val r = Random(3)
        val now = System.currentTimeMillis()
        val events = ArrayList<EventRow>()
        repeat(420) {
            val id = (r.nextDouble().let { it * it } * 30).toLong()
            val t = now - r.nextLong(0, 29L * 86_400_000) - r.nextLong(0, 3_600_000)
            val dur = songs[id.toInt()].durationMs
            val o = when { id % 7 == 3L && r.nextBoolean() -> Outcome.SKIPPED; r.nextInt(10) == 0 -> Outcome.PARTIAL; else -> Outcome.PLAYED }
            val listened = when (o) { Outcome.PLAYED -> dur; Outcome.SKIPPED -> r.nextLong(2_000, 29_000); else -> dur / 3 }
            events += EventRow(id, t, listened, dur, o)
        }
        val stats = events.groupBy { it.songId }.map { (id, ev) ->
            SongStatsEntity(id, plays = ev.count { it.outcome == Outcome.PLAYED }, skips = ev.count { it.outcome == Outcome.SKIPPED },
                partials = ev.count { it.outcome == Outcome.PARTIAL }, listenMs = ev.sumOf { it.listenedMs },
                firstPlayedAt = ev.minOf { it.startedAt }, lastPlayedAt = ev.maxOf { it.startedAt },
                affinityRaw = ev.sumOf { if (it.outcome == Outcome.PLAYED) 0.6 else -0.8 }, affinityUpdatedAt = now, favorite = id % 9 == 0L)
        }
        return events.sortedBy { it.startedAt } to stats
    }

    @Test
    fun statsOverview() {
        assumeTrue(System.getProperty("screenshots") != null)
        val (events, stats) = fakeData()
        val snap = StatsRepository.compute(StatsRange.Month, 0, events, stats, songs, events.first().startedAt)
        compose.setContent {
            WavelengthTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    StatsScreen(snap, StatsRange.Month, emptyList(), songs.associateBy { it.id }, {}, {}, {}, {}, {}, {}, PaddingValues())
                }
            }
        }
        save("stats-overview")
    }

    @Test
    fun songSheet() {
        assumeTrue(System.getProperty("screenshots") != null)
        val (events, stats) = fakeData()
        val s = stats.maxBy { it.plays }
        val song = songs[s.songId.toInt()]
        val list = events.filter { it.songId == s.songId }.takeLast(8).mapIndexed { i, e ->
            PlayEventEntity(i.toLong(), e.songId, e.startedAt, e.startedAt + e.listenedMs, e.listenedMs, e.durationMs, e.outcome, i == 2)
        }.reversed()
        compose.setContent { WavelengthTheme { SongStatsSheet(song, s, list, {}, {}, {}) } }
        save("song-sheet")
    }
}
