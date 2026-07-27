package com.dewijones92.totum.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val backup = Backup(
        createdAtEpochMs = 1_700_000_000_000,
        appVersion = "0.1.99",
        subscriptions = listOf(
            BackupSubscription(
                id = "https://feeds.example.com/show.rss",
                title = "A Show",
                url = "https://feeds.example.com/show.rss",
                kind = "podcast",
                subscribedAtEpochMs = 1_600_000_000_000,
            ),
        ),
        playlists = listOf(
            BackupPlaylist(
                name = "Later",
                items = listOf(
                    BackupItem(
                        itemId = "abc",
                        sourceId = "src",
                        title = "An episode",
                        contentKind = "STANDARD",
                        playbackType = "PODCAST",
                        handle = null,
                    ),
                ),
            ),
        ),
        progress = listOf(BackupProgress("abc", positionMs = 42_000, durationMs = 600_000)),
        settings = mapOf("playbackMode" to "AUDIO"),
    )

    @Test
    fun `a backup survives a round trip intact`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup))

        assertEquals(BackupReadResult.Ok(backup), decoded)
    }

    /**
     * The point of the version field: a file from a newer build may hold sections this
     * one does not know how to restore, and a partial restore reporting success would be
     * worse than a clear refusal.
     */
    @Test
    fun `a file from a newer build is refused, not half-read`() {
        val fromTheFuture = BackupCodec.encode(backup.copy(version = Backup.CURRENT_VERSION + 1))

        val result = BackupCodec.decode(fromTheFuture)

        assertTrue("expected TooNew, got $result", result is BackupReadResult.TooNew)
    }

    /** An older file is fine — a section it lacks simply restores nothing. */
    @Test
    fun `an older file with missing sections still reads`() {
        val sparse = """{"version":1,"subscriptions":[]}"""

        val result = BackupCodec.decode(sparse)

        assertTrue("expected Ok, got $result", result is BackupReadResult.Ok)
        assertEquals(emptyList<BackupPlaylist>(), (result as BackupReadResult.Ok).backup.playlists)
    }

    /** Arbitrary bytes a user pointed at us are an answer, not a crash. */
    @Test
    fun `something that is not a backup is reported rather than thrown`() {
        listOf("", "not json at all", "<opml/>", "[1,2,3]").forEach { junk ->
            val result = BackupCodec.decode(junk)
            assertTrue("expected Unreadable for '$junk', got $result", result is BackupReadResult.Unreadable)
        }
        // "{}" IS a valid empty backup: every field has a default, so it restores nothing
        // rather than failing. Worth stating, since it looks like it should be an error.
        assertTrue(BackupCodec.decode("{}") is BackupReadResult.Ok)
    }

    /** Unknown fields are tolerated, so a newer build's extra keys do not break the read. */
    @Test
    fun `unknown fields are ignored`() {
        val withExtra = """{"version":1,"somethingNew":{"a":1},"subscriptions":[]}"""

        assertTrue(BackupCodec.decode(withExtra) is BackupReadResult.Ok)
    }
}
