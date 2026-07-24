package com.dewijones92.uniapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FeedEntity::class,
        EpisodeEntity::class,
        DownloadEntity::class,
        PlaybackProgressEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistItemEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
public abstract class UniAppDatabase : RoomDatabase() {

    public abstract fun podcastDao(): PodcastDao

    public abstract fun downloadDao(): DownloadDao

    public abstract fun playbackProgressDao(): PlaybackProgressDao

    public abstract fun localPlaylistDao(): LocalPlaylistDao

    public companion object {
        public fun build(context: Context): UniAppDatabase =
            Room.databaseBuilder(context, UniAppDatabase::class.java, "uniapp.db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .build()

        /** v8: local (cross-pillar) playlists + their items. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS local_playlists (" +
                        "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS local_playlist_items (" +
                        "playlistId TEXT NOT NULL, itemId TEXT NOT NULL, position INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, author TEXT, thumbnailUrl TEXT, sourceId TEXT NOT NULL, " +
                        "contentKind TEXT NOT NULL, playbackType TEXT NOT NULL, handle TEXT, mediaUrl TEXT, " +
                        "PRIMARY KEY(playlistId, itemId), " +
                        "FOREIGN KEY(playlistId) REFERENCES local_playlists(id) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_local_playlist_items_playlistId " +
                        "ON local_playlist_items(playlistId)",
                )
            }
        }

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

        /** v5: playback_progress table (resume position per item). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_progress (" +
                        "mediaItemId TEXT NOT NULL PRIMARY KEY, " +
                        "positionMs INTEGER NOT NULL, " +
                        "durationMs INTEGER, " +
                        "updatedAtEpochMs INTEGER NOT NULL)",
                )
            }
        }

        /**
         * v6: sources gained an `origin` ('manual' | 'youtube_import'). Existing
         * rows default to 'manual' — the safe choice, since it means an account
         * sync never prunes anything already here; new imports mark themselves.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_feeds ADD COLUMN origin TEXT NOT NULL DEFAULT 'manual'")
            }
        }

        /** v7: episodes gained a `chapters` JSON column (nullable); existing rows have none. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_episodes ADD COLUMN chapters TEXT")
            }
        }
    }
}
