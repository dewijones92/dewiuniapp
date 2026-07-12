package com.dewijones92.uniapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeedEntity::class, EpisodeEntity::class],
    version = 2,
    exportSchema = false,
)
public abstract class UniAppDatabase : RoomDatabase() {

    public abstract fun podcastDao(): PodcastDao

    public companion object {
        public fun build(context: Context): UniAppDatabase =
            Room.databaseBuilder(context, UniAppDatabase::class.java, "uniapp.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: episodes gained an author column (notification artist line). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_episodes ADD COLUMN author TEXT")
            }
        }
    }
}
