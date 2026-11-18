package com.wavelength.player.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Saved responses from Last.fm / Deezer, so Discover doesn't ask for the same thing twice. */
@Entity(tableName = "api_cache")
data class ApiCacheEntity(@PrimaryKey val key: String, val body: String, val fetchedAt: Long)

/** Your reaction to a recommended song, and whether it's on your wishlist. */
@Entity(tableName = "discover_items")
data class DiscoverItemEntity(
    /** Normalised "artist|title". */
    @PrimaryKey val key: String,
    val artist: String,
    val title: String,
    /** 1 = thumbs up, -1 = thumbs down, 0 = none. */
    val verdict: Int = 0,
    val wishlisted: Boolean = false,
    val wishlistedAt: Long? = null,
    /** Previews heard for 20+ seconds / stopped within 5 seconds. */
    val previewListens: Int = 0,
    val previewSkips: Int = 0,
    val coverUrl: String? = null,
    val link: String? = null,
    val updatedAt: Long = 0,
)

@Dao
interface DiscoverDao {
    @Query("SELECT * FROM api_cache WHERE `key` = :key AND fetchedAt >= :freshAfter")
    suspend fun cached(key: String, freshAfter: Long): ApiCacheEntity?

    @Upsert
    suspend fun putCache(entry: ApiCacheEntity)

    @Query("DELETE FROM api_cache")
    suspend fun clearCache()

    @Query("SELECT * FROM discover_items")
    fun observeItems(): Flow<List<DiscoverItemEntity>>

    @Query("SELECT * FROM discover_items WHERE `key` = :key")
    suspend fun item(key: String): DiscoverItemEntity?

    @Upsert
    suspend fun putItem(item: DiscoverItemEntity)
}
