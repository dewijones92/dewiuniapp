package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedMediaTest {

    private val watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=abc123")

    private fun item() = MediaItem(
        id = MediaItemId("abc123"),
        sourceId = SourceId("src"),
        title = "A thing",
        publishedAt = null,
        duration = null,
    )

    private fun downloaded(handle: PlayHandle, audioOnly: Boolean) =
        DownloadedMedia(PlayableItem(item(), handle), "/data/abc.media", audioOnly)

    @Test
    fun `a downloaded video plays from disk as a video`() {
        val entry = downloaded(PlayHandle.Video(watchUrl), audioOnly = false)
        assertEquals(PlayHandle.LocalVideo("/data/abc.media"), entry.offline.handle)
    }

    /** The queue fetches audio only; that file has no picture in it, so it plays as audio. */
    @Test
    fun `a video fetched audio-only plays from disk as audio`() {
        val entry = downloaded(PlayHandle.Video(watchUrl), audioOnly = true)
        assertEquals(PlayHandle.Podcast("/data/abc.media"), entry.offline.handle)
    }

    @Test
    fun `a downloaded podcast plays from disk`() {
        val entry = downloaded(PlayHandle.Podcast(), audioOnly = false)
        assertEquals(PlayHandle.Podcast("/data/abc.media"), entry.offline.handle)
    }

    /** An audio-only video is still a video — that is what the row labels itself. */
    @Test
    fun `the pillar survives an audio-only fetch`() {
        assertEquals(MediaKind.VIDEO, downloaded(PlayHandle.Video(watchUrl), audioOnly = true).pillar)
        assertEquals(MediaKind.PODCAST, downloaded(PlayHandle.Podcast(), audioOnly = false).pillar)
    }
}
