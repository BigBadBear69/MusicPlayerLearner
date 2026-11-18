package com.wavelength.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SongEntity::class], version = 1, exportSchema = true)
abstract class WavelengthDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        fun create(context: Context): WavelengthDatabase =
            Room.databaseBuilder(context, WavelengthDatabase::class.java, "wavelength.db").build()
    }
}
