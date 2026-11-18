package com.wavelength.player.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** How a single listen ended. Stored as an int. */
object Outcome {
    /** Heard at least half the song (or 4 minutes). */
    const val PLAYED = 0
    /** Moved on before 30 seconds. */
    const val SKIPPED = 1
    /** Listened past 30 seconds but stopped before half. */
    const val PARTIAL = 2
}

/** One listen of one song: the raw history everything else is computed from. */
@Entity(tableName = "play_events", indices = [Index("songId"), Index("startedAt")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val listenedMs: Long,
    val durationMs: Long,
    val outcome: Int,
    /** Played again right after itself (repeat-one, or restarted with "previous"). */
    val replay: Boolean,
) {
    val completion: Float get() = if (durationMs > 0) (listenedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Running totals per song, kept up to date as events arrive. */
@Entity(tableName = "song_stats")
data class SongStatsEntity(
    @PrimaryKey val songId: Long,
    val plays: Int = 0,
    val skips: Int = 0,
    val partials: Int = 0,
    val replays: Int = 0,
    val listenMs: Long = 0,
    val firstPlayedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    /** Affinity before time decay, as of [affinityUpdatedAt]. See Affinity. */
    val affinityRaw: Double = 0.0,
    val affinityUpdatedAt: Long = 0,
    val favorite: Boolean = false,
    val favoritedAt: Long? = null,
) {
    val listens: Int get() = plays + skips + partials
}

/** Lightweight event projection for charts and totals. */
data class EventRow(
    val songId: Long,
    val startedAt: Long,
    val listenedMs: Long,
    val durationMs: Long,
    val outcome: Int,
)

@Dao
interface StatsDao {
    @Insert
    suspend fun insertEvent(event: PlayEventEntity): Long

    @Query("SELECT * FROM song_stats WHERE songId = :songId")
    suspend fun stats(songId: Long): SongStatsEntity?

    @Upsert
    suspend fun upsertStats(stats: SongStatsEntity)

    @Query("SELECT * FROM song_stats")
    fun observeAllStats(): Flow<List<SongStatsEntity>>

    @Query("SELECT songId, startedAt, listenedMs, durationMs, outcome FROM play_events WHERE startedAt >= :from ORDER BY startedAt")
    fun observeEventsSince(from: Long): Flow<List<EventRow>>

    @Query("SELECT MIN(startedAt) FROM play_events")
    fun observeFirstEventAt(): Flow<Long?>

    @Query("SELECT * FROM play_events ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayEventEntity>>

    @Query("SELECT * FROM play_events WHERE songId = :songId ORDER BY startedAt DESC LIMIT :limit")
    fun observeForSong(songId: Long, limit: Int): Flow<List<PlayEventEntity>>

    @Query("SELECT * FROM play_events ORDER BY startedAt")
    suspend fun allEvents(): List<PlayEventEntity>

    @Query("DELETE FROM play_events")
    suspend fun deleteEvents()

    /** Clears listening history but keeps favourites. */
    @Query(
        """UPDATE song_stats SET plays = 0, skips = 0, partials = 0, replays = 0, listenMs = 0,
           firstPlayedAt = NULL, lastPlayedAt = NULL, affinityRaw = 0, affinityUpdatedAt = 0""",
    )
    suspend fun resetTotals()
}
