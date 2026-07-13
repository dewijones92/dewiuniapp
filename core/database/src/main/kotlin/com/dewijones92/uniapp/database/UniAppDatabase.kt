package com.dewijones92.uniapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeedEntity::class, EpisodeEntity::class, DownloadEntity::class],
    version = 4,
    exportSchema = false,
)
public abstract class UniAppDatabase : RoomDatabase() {

    public abstract fun podcastDao(): PodcastDao

    public abstract fun downloadDao(): DownloadDao

    public companion object {
        public fun build(context: Context): UniAppDatabase =
            Room.databaseBuilder(context, UniAppDatabase::class.java, "uniapp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v2: episodes gained an author column (notification artist line). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_episodes ADD COLUMN author TEXT")
            }
        }

        /** v3: downloads table (offline media). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS downloads (" +
                        "mediaItemId TEXT NOT NULL PRIMARY KEY, " +
                        "status TEXT NOT NULL, " +
                        "downloadedBytes INTEGER NOT NULL, " +
                        "totalBytes INTEGER, " +
                        "localPath TEXT, " +
                        "failureReason TEXT)",
                )
            }
        }

        /** v4: sources gained a sourceType ('podcast' | 'channel'); existing rows are podcasts. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_feeds ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'podcast'")
            }
        }
    }
}
