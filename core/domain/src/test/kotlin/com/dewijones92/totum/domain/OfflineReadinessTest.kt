package com.dewijones92.totum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counting behind "is my queue ready offline?".
 *
 * Every number here ends up on screen, so a wrong one is a lie told confidently — worse than
 * the subtle glyph it replaces.
 */
class OfflineReadinessTest {

    private fun id(n: Int) = MediaItemId("v$n")

    private fun readiness(vararg states: DownloadState): OfflineReadiness {
        val byId = states.mapIndexed { index, state -> id(index) to state }.toMap()
        return OfflineReadiness.of(byId.keys.toList()) { byId.getValue(it) }
    }

    @Test
    fun `each state lands in its own bucket`() {
        val summary = readiness(
            DownloadState.Downloaded("/tmp/a", audioOnly = true),
            DownloadState.Downloading(1, 2),
            DownloadState.NotDownloaded,
            DownloadState.Failed("Join this channel to get access"),
        )

        assertEquals(1, summary.ready)
        assertEquals(1, summary.downloading)
        assertEquals(1, summary.waiting)
        assertEquals(1, summary.unavailableOffline)
        assertEquals(4, summary.total)
    }

    /**
     * A retryable failure counts as WAITING, not as a problem.
     *
     * The app retries it by itself, so showing it as a failure asks for a decision that is not
     * the person's to make — and a flaky connection would then read as a broken queue.
     */
    @Test
    fun `a retryable failure is waiting, a permanent one is not`() {
        assertEquals(1, readiness(DownloadState.Failed("Connection reset by peer")).waiting)
        assertEquals(0, readiness(DownloadState.Failed("Connection reset by peer")).unavailableOffline)

        assertEquals(0, readiness(DownloadState.Failed("Video unavailable")).waiting)
        assertEquals(1, readiness(DownloadState.Failed("Video unavailable")).unavailableOffline)
    }

    /**
     * "Settled" must not wait on something that is never coming — the exact case in Dewi's
     * queue, where 4 members-only videos sat alongside 73 downloaded ones. Treating those as
     * outstanding would leave the queue permanently reading as in-progress.
     */
    @Test
    fun `a queue of downloaded items and permanent failures is settled and complete`() {
        val summary = readiness(
            DownloadState.Downloaded("/tmp/a"),
            DownloadState.Downloaded("/tmp/b"),
            DownloadState.Failed("Join this channel to get access"),
        )

        assertTrue(summary.settled)
        assertTrue(summary.complete)
        assertEquals(2, summary.ready)
    }

    @Test
    fun `anything still downloading or waiting is not settled`() {
        assertFalse(readiness(DownloadState.Downloading(1, 10)).settled)
        assertFalse(readiness(DownloadState.NotDownloaded).settled)
    }

    /** Nothing downloaded is not "complete", however few things are outstanding. */
    @Test
    fun `a queue with nothing downloaded is not complete`() {
        assertFalse(readiness(DownloadState.Failed("Video unavailable")).complete)
        assertFalse(readiness().complete)
    }

    @Test
    fun `an empty queue counts as nothing at all`() {
        val summary = readiness()

        assertEquals(0, summary.total)
        assertTrue(summary.settled)
    }
}
