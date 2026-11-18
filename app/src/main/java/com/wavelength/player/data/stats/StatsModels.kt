package com.wavelength.player.data.stats

import com.wavelength.player.data.db.SongEntity

enum class StatsRange(val label: String) { Week("7 days"), Month("30 days"), Year("12 months"), All("All time") }

/** One bar of the activity chart. */
data class Bucket(val label: String, val startMs: Long, val listenMs: Long, val plays: Int)

/** A song with its numbers for the selected range plus all-time affinity. */
data class SongStat(
    val song: SongEntity,
    val plays: Int,
    val skips: Int,
    val partials: Int,
    val listenMs: Long,
    val avgCompletion: Float,
    val affinity: Int?,
    val favorite: Boolean,
    val lastPlayedAt: Long?,
) {
    val listens get() = plays + skips + partials
    val skipRate: Float get() = if (listens == 0) 0f else skips.toFloat() / listens
}

/** An artist, album or genre total. */
data class GroupStat(val name: String, val subtitle: String, val listenMs: Long, val plays: Int, val cover: SongEntity)

data class StatsSnapshot(
    val range: StatsRange,
    val listenMs: Long = 0,
    val plays: Int = 0,
    val skips: Int = 0,
    val partials: Int = 0,
    val distinctSongs: Int = 0,
    val avgCompletion: Float = 0f,
    val activeDays: Int = 0,
    val currentStreakDays: Int = 0,
    val buckets: List<Bucket> = emptyList(),
    val bucketUnit: String = "day",
    val hourly: LongArray = LongArray(24),
    val topSongs: List<SongStat> = emptyList(),
    val topArtists: List<GroupStat> = emptyList(),
    val topAlbums: List<GroupStat> = emptyList(),
    val topGenres: List<GroupStat> = emptyList(),
    val mostSkipped: List<SongStat> = emptyList(),
    /** All time, highest affinity. */
    val onYourWavelength: List<SongStat> = emptyList(),
    /** High affinity but not played for 30+ days. */
    val forgottenFavorites: List<SongStat> = emptyList(),
    /** Every song in the library with all-time numbers (for the Songs table). */
    val allSongs: List<SongStat> = emptyList(),
    val neverPlayed: Int = 0,
    val librarySize: Int = 0,
) {
    val listens get() = plays + skips + partials
    val skipRate: Float get() = if (listens == 0) 0f else skips.toFloat() / listens
    val isEmpty get() = listens == 0
}
