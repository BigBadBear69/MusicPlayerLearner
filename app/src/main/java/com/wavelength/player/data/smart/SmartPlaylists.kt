package com.wavelength.player.data.smart

import com.wavelength.player.data.db.EventRow
import com.wavelength.player.data.db.Outcome
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.stats.Affinity
import java.time.Instant
import java.time.ZoneId

/** A playlist Wavelength builds and keeps up to date from your listening. */
data class SmartPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val songs: List<SongEntity>,
)

enum class TimeSlot(val label: String, val hours: IntRange) {
    Morning("Morning", 5..11), Afternoon("Afternoon", 12..16), Evening("Evening", 17..21), Night("Late night", 22..28);

    fun contains(hour: Int) = hour in hours || (hour + 24) in hours

    companion object {
        fun of(hour: Int) = entries.first { it.contains(hour) }
    }
}

object SmartPlaylists {
    private const val DAY = 86_400_000L

    fun build(
        songs: List<SongEntity>,
        stats: Map<Long, SongStatsEntity>,
        events: List<EventRow>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SmartPlaylist> {
        fun affinity(id: Long): Int? = stats[id]?.takeIf { it.listens > 0 || it.favorite }
            ?.let { Affinity.score(it.affinityRaw, it.affinityUpdatedAt, it.favorite, now) }
        val scored = songs.map { it to affinity(it.id) }
        val loved = scored.filter { (it.second ?: 0) >= 60 }.sortedByDescending { it.second }.map { it.first }

        val onYourWavelength = loved.take(100)

        val neverPlayed = songs.filter { (stats[it.id]?.listens ?: 0) == 0 }.sortedByDescending { it.dateAddedSec }

        val rediscover = scored
            .filter { (s, a) -> (a ?: 0) >= 60 && (stats[s.id]?.lastPlayedAt ?: now) < now - 30 * DAY }
            .sortedByDescending { it.second }.map { it.first }.take(100)

        val skipGraveyard = songs.filter { s ->
            val st = stats[s.id] ?: return@filter false
            st.listens >= 3 && st.skips.toFloat() / st.listens >= 0.6f && !st.favorite
        }.sortedByDescending { stats[it.id]!!.skips }

        val hiddenGems = hiddenGems(songs, stats, loved)

        val slots = timeOfDay(songs, stats, events, now, zone)

        return buildList {
            add(SmartPlaylist("wavelength", "On Your Wavelength", "Your highest Affinity Scores: the songs you love most right now", onYourWavelength))
            add(SmartPlaylist("gems", "Hidden Gems", "Songs from artists, albums and genres you love that you've barely played", hiddenGems))
            add(SmartPlaylist("rediscover", "Rediscover", "Old favourites you haven't played in over a month", rediscover))
            add(SmartPlaylist("never", "Never Played", "Music on your phone you've never listened to, newest first", neverPlayed))
            slots.forEach { (slot, list) ->
                add(SmartPlaylist("time-${slot.name.lowercase()}", "${slot.label} Mix", "What you usually play in the ${slot.label.lowercase()}", list))
            }
            add(SmartPlaylist("graveyard", "Skip Graveyard", "Songs you skip almost every time. Maybe time to delete them?", skipGraveyard))
        }
    }

    /**
     * Rarely played songs that resemble your favourites: same artist, album, genre or decade.
     * Each trait is weighted by how much of your favourite listening it accounts for.
     */
    fun hiddenGems(songs: List<SongEntity>, stats: Map<Long, SongStatsEntity>, loved: List<SongEntity>): List<SongEntity> {
        if (loved.isEmpty()) return emptyList()
        val lovedIds = loved.mapTo(HashSet()) { it.id }
        fun <K> profile(key: (SongEntity) -> K?): Map<K, Double> {
            val counts = loved.mapNotNull(key).groupingBy { it }.eachCount()
            val max = counts.values.maxOrNull()?.toDouble() ?: return emptyMap()
            return counts.mapValues { it.value / max }
        }
        val artists = profile { it.artist }
        val albums = profile { it.albumId }
        val genres = profile { it.genre?.lowercase() }
        val decades = profile { it.year.takeIf { y -> y > 0 }?.let { y -> y / 10 } }

        return songs.asSequence()
            .filter { it.id !in lovedIds }
            .filter { s ->
                val st = stats[s.id]
                (st?.plays ?: 0) <= 2 && (st == null || st.listens < 3 || st.skips.toFloat() / st.listens < 0.5f)
            }
            .map { s ->
                s to (1.0 * (artists[s.artist] ?: 0.0) + 0.6 * (albums[s.albumId] ?: 0.0) +
                    0.5 * (s.genre?.lowercase()?.let { genres[it] } ?: 0.0) +
                    0.2 * (s.year.takeIf { it > 0 }?.let { decades[it / 10] } ?: 0.0))
            }
            .filter { it.second >= 0.3 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(60)
            .toList()
    }

    /** For each part of the day, songs you've actually listened to then (last 90 days), best first. */
    fun timeOfDay(
        songs: List<SongEntity>,
        stats: Map<Long, SongStatsEntity>,
        events: List<EventRow>,
        now: Long,
        zone: ZoneId,
    ): List<Pair<TimeSlot, List<SongEntity>>> {
        val byId = songs.associateBy { it.id }
        val since = now - 90 * DAY
        val perSlot = HashMap<TimeSlot, HashMap<Long, Double>>()
        events.forEach { e ->
            if (e.startedAt < since || e.outcome == Outcome.SKIPPED) return@forEach
            val hour = Instant.ofEpochMilli(e.startedAt).atZone(zone).hour
            val slot = TimeSlot.of(hour)
            // Full plays count most; recent listens count more than old ones.
            val recency = 1.0 - (now - e.startedAt).toDouble() / (90 * DAY) * 0.5
            val w = (if (e.outcome == Outcome.PLAYED) 1.0 else 0.4) * recency
            perSlot.getOrPut(slot) { HashMap() }.merge(e.songId, w, Double::plus)
        }
        // Start with the part of the day it is now.
        val current = TimeSlot.of(Instant.ofEpochMilli(now).atZone(zone).hour)
        return TimeSlot.entries.sortedBy { if (it == current) 0 else 1 }.mapNotNull { slot ->
            val list = perSlot[slot].orEmpty().entries
                .filter { (id, _) -> stats[id]?.let { st -> st.listens < 3 || st.skips.toFloat() / st.listens < 0.6f } != false }
                .sortedByDescending { it.value }
                .mapNotNull { byId[it.key] }
                .take(50)
            if (list.size >= 5) slot to list else null
        }
    }
}
