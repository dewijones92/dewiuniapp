package com.dewijones92.uniapp.queue

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.download.fake.FakeDownloadManager
import com.dewijones92.uniapp.data.queue.QueueEntry
import com.dewijones92.uniapp.data.queue.QueueSnapshot
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.PlayHandle
import com.dewijones92.uniapp.domain.PlayableItem
import com.dewijones92.uniapp.domain.SourceId
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

    private fun downloader(enabled: Boolean = true, allowedOnNetwork: Boolean = true) = QueueAutoDownloader(
        queue = queue,
        downloads = downloads,
        scope = CoroutineScope(dispatcher),
        isEnabled = { enabled },
        isAllowedOnThisNetwork = { allowedOnNetwork },
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

    @Test
    fun `an item with nothing fetchable yet is skipped`() = runTest(dispatcher) {
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
}
