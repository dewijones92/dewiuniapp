package com.dewijones92.totum.data.download

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.asPlayable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RoutedDownloadStrategyTest {

    private fun item(url: String) = MediaItem(
        id = MediaItemId(url),
        sourceId = SourceId("s"),
        title = "t",
        publishedAt = null,
        duration = null,
        mediaUrl = HttpUrl.of(url),
    )

    private fun labelled(label: String) =
        DownloadStrategy { _, _, _ -> flowOf(DownloadState.Downloaded(label)) }

    private val routed = RoutedDownloadStrategy(video = labelled("video"), podcast = labelled("podcast"))

    private suspend fun route(item: PlayableItem) =
        (routed.download(item, File("t"), audioOnly = false).first() as DownloadState.Downloaded).localPath

    @Test
    fun `a video handle takes the video route`() = runTest {
        assertEquals("video", route(item("https://www.youtube.com/watch?v=abc").asPlayable()))
    }

    @Test
    fun `a podcast handle takes the podcast route`() = runTest {
        assertEquals("podcast", route(item("https://cdn.example.com/ep.mp3").asPlayable()))
    }

    /**
     * The handle decides, not the URL. A queued video's mediaUrl is a resolved stream — or
     * absent entirely — so routing on that is what used to send it down the podcast path.
     */
    @Test
    fun `a video with a non-youtube media url still takes the video route`() = runTest {
        val watch = HttpUrl.of("https://www.youtube.com/watch?v=abc")
        val queued = PlayableItem(item("https://rr1.googlevideo.com/expired"), PlayHandle.Video(watch))
        assertEquals("video", route(queued))
    }

    @Test
    fun `an already-local video still routes as a video`() = runTest {
        val local = PlayableItem(item("https://cdn.example.com/ep.mp3"), PlayHandle.LocalVideo("/tmp/x"))
        assertEquals("video", route(local))
    }
}
