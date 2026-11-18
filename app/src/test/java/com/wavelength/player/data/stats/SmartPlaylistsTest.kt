package com.wavelength.player.data.stats

import com.wavelength.player.data.db.EventRow
import com.wavelength.player.data.db.Outcome
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.smart.SmartPlaylists
import com.wavelength.player.data.smart.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SmartPlaylistsTest {
    private val day = 86_400_000L
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 11, 18).atTime(20, 0).toInstant(zone).toEpochMilli() // 8pm

    private fun song(id: Long, artist: String, album: Long, genre: String) =
        SongEntity(id, "S$id", artist, "A$album", album, null, genre, 200_000, 1, 2015, "Music/", "s$id.mp3", null, 0, 0, id)

    private val songs = listOf(
        song(1, "Loved", 1, "Rock"), song(2, "Loved", 1, "Rock"), // favourites
        song(3, "Loved", 2, "Rock"),                               // same artist, unplayed → hidden gem
        song(4, "Other", 3, "Rock"),                               // same genre → weaker gem
        song(5, "Stranger", 4, "Polka"),                           // nothing in common
        song(6, "Skippy", 5, "Pop"),                               // always skipped
    )
    private val stats = mapOf(
        1L to SongStatsEntity(1, plays = 10, affinityRaw = 4.0, affinityUpdatedAt = now, lastPlayedAt = now - 40 * day),
        2L to SongStatsEntity(2, plays = 8, affinityRaw = 3.0, affinityUpdatedAt = now, lastPlayedAt = now - day),
        6L to SongStatsEntity(6, skips = 5, plays = 0, affinityRaw = -4.0, affinityUpdatedAt = now, lastPlayedAt = now),
    )

    @Test
    fun buildsTheExpectedLists() {
        val lists = SmartPlaylists.build(songs, stats, emptyList(), now, zone).associateBy { it.id }
        assertEquals(listOf(1L, 2L), lists.getValue("wavelength").songs.map { it.id })
        assertEquals(listOf(1L), lists.getValue("rediscover").songs.map { it.id }) // song 1 not played for 40 days
        assertEquals(listOf(6L), lists.getValue("graveyard").songs.map { it.id })
        assertEquals(setOf(3L, 4L, 5L), lists.getValue("never").songs.map { it.id }.toSet())
        val gems = lists.getValue("gems").songs.map { it.id }
        assertEquals(3L, gems.first())
        assertTrue(4L in gems)
        assertFalse(5L in gems)
        assertFalse(6L in gems)
    }

    @Test
    fun timeOfDayGroupsListensAndPutsTheCurrentSlotFirst() {
        val events = (1..6).flatMap { id ->
            listOf(
                EventRow(id.toLong(), now - day - 1 * 3_600_000, 200_000, 200_000, Outcome.PLAYED), // 7pm → evening
                EventRow(id.toLong(), now - day - 12 * 3_600_000, 200_000, 200_000, Outcome.PLAYED), // 8am → morning
            )
        }
        val slots = SmartPlaylists.timeOfDay(songs, emptyMap(), events, now, zone)
        assertEquals(TimeSlot.Evening, slots.first().first) // it's 8pm
        assertEquals(setOf(TimeSlot.Evening, TimeSlot.Morning), slots.map { it.first }.toSet())
        assertEquals(TimeSlot.Night, TimeSlot.of(2))
        assertEquals(TimeSlot.Night, TimeSlot.of(23))
    }

    @Test
    fun shuffleStylesWeightSongsAsDescribed() {
        val loved = stats.getValue(1L)
        val skipped = stats.getValue(6L)
        val random = com.wavelength.player.data.smart.ShuffleStyle.Random
        val smart = com.wavelength.player.data.smart.ShuffleStyle.Smart
        val discovery = com.wavelength.player.data.smart.ShuffleStyle.Discovery
        assertEquals(1.0, random.weight(loved, now), 0.0)
        assertTrue(smart.weight(loved, now) > 2.0)
        assertTrue(smart.weight(skipped, now) < 0.5)
        assertEquals(1.0, smart.weight(null, now), 1e-9)
        // Discovery: never played beats played-10-times.
        assertTrue(discovery.weight(null, now) > 4 * discovery.weight(loved, now))
    }
}
