package com.wavelength.player.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One slot in a playlist. Song ids aren't foreign keys on purpose: if a file disappears for a while
 * (SD card removed, file moved) the playlist keeps its place and the song reappears once it's back.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("songId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val position: Int,
    val songId: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    @ColumnInfo(name = "songIds") val songIdsCsv: String?,
) {
    val songIds: List<Long> get() = songIdsCsv?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.updatedAt,
               (SELECT GROUP_CONCAT(songId) FROM
                   (SELECT songId FROM playlist_songs s WHERE s.playlistId = p.id ORDER BY s.position)) AS songIds
        FROM playlists p ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :id ORDER BY position")
    suspend fun songIds(id: Long): List<Long>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearSongs(id: Long)

    @Insert
    suspend fun insertSongs(rows: List<PlaylistSongEntity>)

    /** Replaces a playlist's contents (positions are rewritten as 0..n-1). */
    @Transaction
    suspend fun setSongs(id: Long, songIds: List<Long>, now: Long) {
        clearSongs(id)
        insertSongs(songIds.mapIndexed { i, s -> PlaylistSongEntity(id, i, s) })
        touch(id, now)
    }

    @Transaction
    suspend fun create(name: String, songIds: List<Long>, now: Long): Long {
        val id = insert(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
        insertSongs(songIds.mapIndexed { i, s -> PlaylistSongEntity(id, i, s) })
        return id
    }
}
