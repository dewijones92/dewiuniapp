package com.dewijones92.uniapp.ytdlp

import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FakeYtDlpEngineTest {

    private val engine = FakeYtDlpEngine()
    private val knownUrl = MediaUrl.of("https://example.com/watch?v=known")

    @Test
    fun `extract returns registered metadata`() = runTest {
        val metadata = FakeYtDlpEngine.sampleMetadata()
        engine.registerMedia(knownUrl, metadata)

        assertEquals(ExtractionResult.Success(metadata), engine.extract(knownUrl))
    }

    @Test
    fun `extract reports unsupported url as a value`() = runTest {
        val unknown = MediaUrl.of("https://example.com/unknown")

        assertEquals(ExtractionResult.Failure.UnsupportedUrl(unknown), engine.extract(unknown))
    }

    @Test
    fun `download emits started then monotonic progress then completed`() = runTest {
        engine.registerMedia(knownUrl, FakeYtDlpEngine.sampleMetadata())

        val events = engine.download(DownloadRequest(knownUrl, File("/tmp"))).toList()

        assertTrue(events.first() is DownloadEvent.Started)
        assertTrue(events.last() is DownloadEvent.Completed)
        val progress = events.filterIsInstance<DownloadEvent.Progress>()
        assertTrue(progress.isNotEmpty())
        assertEquals(progress.map { it.bytesDownloaded }.sorted(), progress.map { it.bytesDownloaded })
        assertEquals(1.0, progress.last().fraction)
    }

    @Test
    fun `download of unknown url fails without progress`() = runTest {
        val unknown = MediaUrl.of("https://example.com/unknown")

        val events = engine.download(DownloadRequest(unknown, File("/tmp"))).toList()

        assertTrue(events.first() is DownloadEvent.Started)
        assertTrue(events.last() is DownloadEvent.Failed)
        assertTrue(events.filterIsInstance<DownloadEvent.Progress>().isEmpty())
    }
}
