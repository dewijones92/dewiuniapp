package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineDownloadStrategyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val engine = FakeYtDlpEngine()
    private val watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=abc")

    private fun videoItem(url: HttpUrl? = watchUrl) = MediaItem(
        id = MediaItemId("abc"),
        sourceId = SourceId("https://www.youtube.com/@chan"),
        title = "A video",
        publishedAt = null,
        duration = null,
        mediaUrl = url,
    )

    @Test
    fun `merges through the engine and moves the result onto target`() = runTest {
        engine.registerMedia(watchUrl, FakeYtDlpEngine.sampleMetadata(id = "abc"))
        val target = temp.newFile("out.media")

        val states = EngineDownloadStrategy(engine).download(videoItem(), target).toList()

        assertTrue(states.first() is DownloadState.Downloading)
        val done = states.last() as DownloadState.Downloaded
        assertEquals(target.absolutePath, done.localPath)
        assertTrue(target.exists())
        // The temp work directory is cleaned up.
        assertFalse(temp.root.resolve("out.part").exists())
    }

    @Test
    fun `reports failure when the item has no url`() = runTest {
        val states = EngineDownloadStrategy(engine)
            .download(videoItem(url = null), temp.newFile("x.media")).toList()

        assertTrue(states.single() is DownloadState.Failed)
    }

    @Test
    fun `surfaces an engine failure for an unresolvable video`() = runTest {
        val states = EngineDownloadStrategy(engine)
            .download(videoItem(), temp.newFile("x.media")).toList()

        assertTrue(states.last() is DownloadState.Failed)
    }

    @Test
    fun `handles only streaming-page urls`() {
        assertTrue(EngineDownloadStrategy.handles(videoItem()))
        assertTrue(EngineDownloadStrategy.handles(videoItem(HttpUrl.of("https://youtu.be/abc"))))
        assertFalse(EngineDownloadStrategy.handles(videoItem(HttpUrl.of("https://cdn.example.com/a.mp3"))))
        assertFalse(EngineDownloadStrategy.handles(videoItem(url = null)))
    }
}
