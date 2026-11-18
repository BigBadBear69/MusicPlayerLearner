package com.wavelength.player.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT id FROM songs")
    suspend fun allIds(): List<Long>

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Replace the library contents with a fresh scan, keeping rows (and their ids) that still exist. */
    @Transaction
    suspend fun sync(scanned: List<SongEntity>) {
        val keep = scanned.mapTo(HashSet()) { it.id }
        val gone = allIds().filterNot { it in keep }
        gone.chunked(500).forEach { deleteByIds(it) }
        scanned.chunked(500).forEach { upsertAll(it) }
    }
}
