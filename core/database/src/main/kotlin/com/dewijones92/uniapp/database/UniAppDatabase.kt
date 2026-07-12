package com.dewijones92.uniapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FeedEntity::class, EpisodeEntity::class],
    version = 1,
    exportSchema = false,
)
public abstract class UniAppDatabase : RoomDatabase() {

    public abstract fun podcastDao(): PodcastDao

    public companion object {
        public fun build(context: Context): UniAppDatabase =
            Room.databaseBuilder(context, UniAppDatabase::class.java, "uniapp.db").build()
    }
}
