package com.wavelength.player.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class WavelengthDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): WavelengthDatabase =
            Room.databaseBuilder(context, WavelengthDatabase::class.java, "wavelength.db").build()
    }
}
