package com.dewijones92.totum.queue

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.download.fake.FakeDownloadManager
import com.dewijones92.totum.data.queue.QueueEntry
import com.dewijones92.totum.data.queue.QueueSnapshot
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueAutoDownloaderTest {

    private val dispatcher = StandardTestDispatcher()
    private val downloads = FakeDownloadManager()
    private val queue = MutableStateFlow(QueueSnapshot())

    private fun item(id: String, url: String? = "https://example.com/$id.mp3") = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId("feed"),
        title = id,
        publishedAt = null,
        duration = null,
        mediaUrl = url?.let(HttpUrl::of),
    )

    private fun entry(id: String, url: String? = "https://example.com/$id.mp3") =
        QueueEntry(PlayableItem(item(id, url), PlayHandle.Podcast()))

    private fun downloader(
        enabled: Boolean = true,
        allowedOnNetwork: Boolean = true,
        maxAttempts: Int = 3,
    ) = QueueAutoDownloader(
        queue = queue,
        downloads = downloads,
        scope = CoroutineScope(dispatcher),
        isEnabled = { enabled },
        isAllowedOnThisNetwork = { allowedOnNetwork },
        maxAttempts = maxAttempts,
    )

    @Test
    fun `every queued item has its audio fetched`() = runTest(dispatcher) {
        downloader().start()
        queue.value = QueueSnapshot(listOf(entry("a"), entry("b")))
        advanceUntilIdle()

        assertEquals(listOf("a" to true, "b" to true), downloads.requested.map { it.first.value to it.second })
    }

    @Test
    fun `it asks for audio only, never the full media`() = runTest(dispatcher) {
        downloader().start()
        queue.value = QueueSnapshot(listOf(entry("a")))
        advanceUntilIdle()

        assertTrue("auto-downloads must be audio-only", downloads.requested.all { it.second })
    }

    @Test
    fun `nothing is fetched when the setting is off`() = runTest(dispatcher) {
        downloader(enabled = false).start()
        queue.value = QueueSnapshot(listOf(entry("a")))
        advanceUntilIdle()

        assertTrue(downloads.requested.isEmpty())
    }

    @Test
    fun `nothing is fetched on a disallowed network`() = runTest(dispatcher) {
        downloader(allowedOnNetwork = false).start()
        queue.value = QueueSnapshot(listOf(entry("a")))
        advanceUntilIdle()

        assertTrue(downloads.requested.isEmpty())
    }

    @Test
    fun `an item already downloaded is not fetched again`() = runTest(dispatcher) {
        downloads.download(item("a"), audioOnly = true)
        downloads.requested.clear()

        downloader().start()
        queue.value = QueueSnapshot(listOf(entry("a"), entry("b")))
        advanceUntilIdle()

        assertEquals(listOf("b"), downloads.requested.map { it.first.value })
    }

    /**
     * The bug this replaces: a video queued from search carries no `mediaUrl` (its stream
     * isn't resolved yet), so the old `mediaUrl == null` skip meant it never got its audio —
     * and the "picked up on a later queue change" fallback never fired if nothing changed.
     */
    @Test
    fun `a video with no resolved stream is fetched via its watch url`() = runTest(dispatcher) {
        downloader().start()
        val watch = HttpUrl.of("https://www.youtube.com/watch?v=abc12345678")
        val video = PlayableItem(item("vid", url = null), PlayHandle.Video(watch))
        queue.value = QueueSnapshot(listOf(QueueEntry(video)))
        advanceUntilIdle()

        assertEquals(listOf(MediaItemId("vid") to true), downloads.requested)
    }

    @Test
    fun `the watch url is what gets handed to the downloader`() = runTest(dispatcher) {
        downloader().start()
        val watch = HttpUrl.of("https://www.youtube.com/watch?v=abc12345678")
        // A stale resolved stream must not win over the stable watch URL.
        val stale = item("vid", url = "https://rr1.googlevideo.com/expired")
        queue.value = QueueSnapshot(listOf(QueueEntry(PlayableItem(stale, PlayHandle.Video(watch)))))
        advanceUntilIdle()

        assertEquals(watch, downloads.lastItem?.fetchUrl)
    }

    @Test
    fun `a podcast with nothing fetchable yet is skipped`() = runTest(dispatcher) {
        downloader().start()
        queue.value = QueueSnapshot(listOf(entry("no-url", url = null)))
        advanceUntilIdle()

        assertTrue(downloads.requested.isEmpty())
    }

    @Test
    fun `an already-local video is not fetched`() = runTest(dispatcher) {
        downloader().start()
        val local = PlayableItem(item("local"), PlayHandle.LocalVideo("/tmp/v.mkv"))
        queue.value = QueueSnapshot(listOf(QueueEntry(local)))
        advanceUntilIdle()

        assertTrue(downloads.requested.isEmpty())
    }

    /**
     * The bug from the 2026-07-28 reports: two members-only videos in a 59-item queue were
     * re-attempted on every queue change, on every launch, for days.
     */
    @Test
    fun `a permanently-failed item is never retried`() = runTest(dispatcher) {
        downloads.setFailed(MediaItemId("a"), "ERROR: [youtube] a: Join this channel to get access")
        downloader().start()
        queue.value = QueueSnapshot(listOf(entry("a"), entry("b")))
        advanceUntilIdle()

        assertEquals(listOf("b"), downloads.requested.map { it.first.value })
    }

    @Test
    fun `a transient failure is retried`() = runTest(dispatcher) {
        downloads.setFailed(MediaItemId("a"), "Unable to connect: timeout")
        downloader().start()
        queue.value = QueueSnapshot(listOf(entry("a")))
        advanceUntilIdle()

        assertEquals(listOf("a"), downloads.requested.map { it.first.value })
    }

    /** A flaky connection gets a few more goes; a broken item does not get infinite ones. */
    @Test
    fun `transient retries stop at the attempt limit`() = runTest(dispatcher) {
        downloader(maxAttempts = 2).start()
        repeat(5) { round ->
            downloads.setFailed(MediaItemId("a"), "Unable to connect: timeout")
            queue.value = QueueSnapshot(listOf(entry("a"), entry("pad$round")))
            advanceUntilIdle()
        }

        assertEquals(2, downloads.requested.count { it.first.value == "a" })
    }

    /**
     * The reason a permanently-failed item is skipped does not change between passes, and
     * the report buffer is bounded: three such videos repeated theirs on every queue change
     * and took 14% of a 387-event report (0.1.229) saying nothing new.
     */
    @Test
    fun `a permanent failure is explained once, however many passes it survives`() = runTest(dispatcher) {
        val lines = mutableListOf<String>()
        val previous = Diag.sink
        Diag.sink = Diag.Sink { _, _, message, _ -> if ("not fetching" in message) lines += message }

        downloads.setFailed(MediaItemId("a"), "ERROR: [youtube] a: Join this channel to get access")
        downloader().start()
        repeat(5) {
            queue.value = QueueSnapshot(listOf(entry("a"), entry("b$it")))
            testScheduler.advanceUntilIdle()
        }

        Diag.sink = previous
        assertEquals(1, lines.size)
    }
}
