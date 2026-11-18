package com.wavelength.player.data.stats

import com.wavelength.player.data.db.EventRow
import com.wavelength.player.data.db.Outcome
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val min = 60_000L

    @Test
    fun classifyFollowsThePlanRules() {
        // 4-minute song: a play needs 2 minutes.
        assertEquals(Outcome.PLAYED, ListeningRules.classify(2 * min, 4 * min, endedNaturally = false))
        assertEquals(Outcome.PARTIAL, ListeningRules.classify(90_000, 4 * min, endedNaturally = false))
        assertEquals(Outcome.SKIPPED, ListeningRules.classify(12_000, 4 * min, endedNaturally = false))
        // 10-minute song: 4 minutes is enough even though it's under half.
        assertEquals(Outcome.PLAYED, ListeningRules.classify(4 * min, 10 * min, endedNaturally = false))
        // Under a second doesn't count at all.
        assertNull(ListeningRules.classify(400, 4 * min, endedNaturally = false))
        // Ending by itself after a seek near the end is not a skip.
        assertEquals(Outcome.PARTIAL, ListeningRules.classify(20_000, 4 * min, endedNaturally = true))
    }

    @Test
    fun affinityRisesWithPlaysFallsWithSkipsAndFades() {
        val day = 86_400_000L
        var raw = 0.0
        var at = 0L
        repeat(5) { raw = Affinity.update(raw, at, at + day, Affinity.delta(Outcome.PLAYED, 3 * min, 3 * min, false)); at += day }
        val liked = Affinity.score(raw, at, false, at)
        assertTrue("liked=$liked", liked > 70)

        var skippedRaw = 0.0
        repeat(5) { skippedRaw += Affinity.delta(Outcome.SKIPPED, 5_000, 3 * min, false) }
        assertTrue(Affinity.score(skippedRaw, at, false, at) < 30)

        // After one half-life the effect halves.
        assertEquals(raw / 2, Affinity.decay(raw, at, at + (Affinity.HALF_LIFE_DAYS * day).toLong()), 1e-6)
        assertEquals(50, Affinity.score(0.0, 0, false, at))
        assertTrue(Affinity.score(0.0, 0, true, at) > 50)
    }

    private fun song(id: Long, artist: String) = SongEntity(
        id, "Song $id", artist, "Album $artist", id * 10, null, "Rock", 3 * min, 1, 2020, "Music/", "s$id.mp3", null, 0, 0, 0,
    )

    @Test
    fun snapshotTotalsAndRankings() {
        val now = System.currentTimeMillis()
        val songs = listOf(song(1, "A"), song(2, "B"), song(3, "A"), song(4, "C"))
        val events = listOf(
            EventRow(1, now - 3000_000, 3 * min, 3 * min, Outcome.PLAYED),
            EventRow(1, now - 2000_000, 3 * min, 3 * min, Outcome.PLAYED),
            EventRow(2, now - 1000_000, 5_000, 3 * min, Outcome.SKIPPED),
            EventRow(3, now - 500_000, 60_000, 3 * min, Outcome.PARTIAL),
        )
        val stats = listOf(
            SongStatsEntity(1, plays = 2, listenMs = 6 * min, lastPlayedAt = now, affinityRaw = 2.0, affinityUpdatedAt = now),
            SongStatsEntity(2, skips = 1, listenMs = 5_000, lastPlayedAt = now, affinityRaw = -1.0, affinityUpdatedAt = now),
            SongStatsEntity(3, partials = 1, listenMs = 60_000, lastPlayedAt = now),
        )
        val snap = StatsRepository.compute(StatsRange.Week, 0, events, stats, songs, events.first().startedAt, now)
        assertEquals(2, snap.plays)
        assertEquals(1, snap.skips)
        assertEquals(1, snap.partials)
        assertEquals(6 * min + 5_000 + 60_000, snap.listenMs)
        assertEquals(1L, snap.topSongs.first().song.id)
        assertEquals("A", snap.topArtists.first().name)
        assertEquals(2L, snap.mostSkipped.first().song.id)
        assertEquals(1, snap.neverPlayed)
        assertEquals(7, snap.buckets.size)
        assertEquals(snap.listenMs, snap.buckets.sumOf { it.listenMs })
        assertEquals(snap.listenMs, snap.hourly.sum())
        assertEquals(1, snap.currentStreakDays)
        assertEquals(1L, snap.onYourWavelength.first().song.id)
    }
}
