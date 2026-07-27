package com.dewijones92.totum.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The v13 → v14 download migration, which has to carry real files across: a download
 * already on disk must keep its title and stay playable and deletable afterwards.
 *
 * The v13 tables are built by hand rather than by a schema export (the database does not
 * export schemas), so only the tables the migration reads are created.
 */
class DownloadsMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun cleanUp() {
        helper?.close()
        context.deleteDatabase(name)
    }

    private fun openAtV13(): SupportSQLiteDatabase {
        context.deleteDatabase(name)
        val callback = object : SupportSQLiteOpenHelper.Callback(V13) {
            override fun onCreate(db: SupportSQLiteDatabase) = V13_TABLES.forEach(db::execSQL)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return FrameworkSQLiteOpenHelperFactory()
            .create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build())
            .also { helper = it }
            .writableDatabase
    }

    private fun migrate(db: SupportSQLiteDatabase) {
        TotumDatabase.MIGRATIONS.single { it.startVersion == V13 }.migrate(db)
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> =
        query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { cursor.getString(it) })
                }
            }
        }

    @Test
    fun aDownloadedVideoKeepsItsTitleFromTheQueue() {
        val db = openAtV13()
        db.execSQL(
            "INSERT INTO downloads VALUES ('vid-1', 'downloaded', 0, NULL, '/data/vid.media', NULL, 1)",
        )
        db.execSQL(
            "INSERT INTO queue_items (position, groupId, groupTitle, isCurrent, itemId, title, author, " +
                "thumbnailUrl, sourceId, contentKind, playbackType, handle, mediaUrl) VALUES " +
                "(0, NULL, NULL, 1, 'vid-1', 'Backpacking Ben', 'Ben', NULL, 'chan', 'STANDARD', " +
                "'VIDEO', 'https://www.youtube.com/watch?v=abc', NULL)",
        )

        migrate(db)

        val watch = "https://www.youtube.com/watch?v=abc"
        assertEquals(
            listOf(listOf("vid-1", "Backpacking Ben", "VIDEO", watch, "/data/vid.media")),
            db.rows("SELECT itemId, title, playbackType, handle, localPath FROM downloads"),
        )
    }

    @Test
    fun aDownloadedEpisodeKeepsItsTitleFromTheFeed() {
        val db = openAtV13()
        db.execSQL("INSERT INTO downloads VALUES ('ep-1', 'downloaded', 0, NULL, '/data/ep.media', NULL, 0)")
        db.execSQL(
            "INSERT INTO podcast_episodes (id, feedId, title, author, publishedAtEpochMs, durationSeconds, " +
                "description, thumbnailUrl, mediaUrl, chapters) VALUES " +
                "('ep-1', 'feed-1', 'Episode one', 'A host', NULL, NULL, NULL, NULL, 'https://x/ep.mp3', NULL)",
        )

        migrate(db)

        assertEquals(
            listOf(listOf("ep-1", "Episode one", "A host", "feed-1", "PODCAST")),
            db.rows("SELECT itemId, title, author, sourceId, playbackType FROM downloads"),
        )
    }

    /**
     * A file whose item is described nowhere still gets a row. Dropping it would strand
     * the bytes on disk with nothing in the UI able to play or delete them.
     */
    @Test
    fun anUnknownDownloadSurvivesUnderItsOwnId() {
        val db = openAtV13()
        db.execSQL("INSERT INTO downloads VALUES ('orphan', 'downloaded', 0, NULL, '/data/o.media', NULL, 0)")

        migrate(db)

        assertEquals(
            listOf(listOf("orphan", "orphan", "/data/o.media")),
            db.rows("SELECT itemId, title, localPath FROM downloads"),
        )
    }

    /** The migrated table must be exactly what Room builds for a fresh install. */
    @Test
    fun theMigratedTableMatchesAFreshOne() {
        val db = openAtV13()
        db.execSQL("INSERT INTO downloads VALUES ('ep-1', 'downloaded', 0, NULL, '/data/ep.media', NULL, 0)")
        migrate(db)
        val migrated = db.rows("PRAGMA table_info(downloads)").map { it.take(COLUMN_FIELDS) }

        val fresh = Room.inMemoryDatabaseBuilder(context, TotumDatabase::class.java).build()
        val expected = try {
            fresh.openHelper.readableDatabase
                .rows("PRAGMA table_info(downloads)").map { it.take(COLUMN_FIELDS) }
        } finally {
            fresh.close()
        }

        assertEquals(expected, migrated)
    }

    private companion object {
        const val V13 = 13

        /** cid, name, type, notnull — enough to catch a shape mismatch, ignoring column order noise. */
        const val COLUMN_FIELDS = 4

        val V13_TABLES = listOf(
            "CREATE TABLE downloads (mediaItemId TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, " +
                "downloadedBytes INTEGER NOT NULL, totalBytes INTEGER, localPath TEXT, failureReason TEXT, " +
                "audioOnly INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE queue_items (rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "position INTEGER NOT NULL, groupId TEXT, groupTitle TEXT, isCurrent INTEGER NOT NULL DEFAULT 0, " +
                "itemId TEXT NOT NULL, title TEXT NOT NULL, author TEXT, thumbnailUrl TEXT, " +
                "sourceId TEXT NOT NULL, contentKind TEXT NOT NULL, playbackType TEXT NOT NULL, handle TEXT, " +
                "mediaUrl TEXT)",
            "CREATE TABLE play_history (itemId TEXT NOT NULL PRIMARY KEY, lastPlayedAtEpochMs INTEGER NOT NULL, " +
                "title TEXT NOT NULL, author TEXT, thumbnailUrl TEXT, sourceId TEXT NOT NULL, " +
                "contentKind TEXT NOT NULL, playbackType TEXT NOT NULL, handle TEXT, mediaUrl TEXT)",
            "CREATE TABLE local_playlist_items (playlistId TEXT NOT NULL, itemId TEXT NOT NULL, " +
                "position INTEGER NOT NULL, title TEXT NOT NULL, author TEXT, thumbnailUrl TEXT, " +
                "sourceId TEXT NOT NULL, contentKind TEXT NOT NULL, playbackType TEXT NOT NULL, handle TEXT, " +
                "mediaUrl TEXT, PRIMARY KEY(playlistId, itemId))",
            "CREATE TABLE podcast_episodes (id TEXT NOT NULL PRIMARY KEY, feedId TEXT NOT NULL, " +
                "title TEXT NOT NULL, author TEXT, publishedAtEpochMs INTEGER, durationSeconds INTEGER, " +
                "description TEXT, thumbnailUrl TEXT, mediaUrl TEXT, chapters TEXT)",
        )
    }
}
