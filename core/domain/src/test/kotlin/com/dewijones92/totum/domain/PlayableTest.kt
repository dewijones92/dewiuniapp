package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayableTest {

    private val watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=abc123")

    private fun item(id: String = "abc123") = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId("src"),
        title = "A thing",
        publishedAt = null,
        duration = null,
    )

    @Test
    fun `a video handle keeps the watch URL, not a stream URL`() {
        // The point of the handle: streams expire, so what is stored must be the
        // stable page we can re-resolve from.
        val playable = PlayableItem(item(), PlayHandle.Video(watchUrl))
        assertEquals(watchUrl, (playable.handle as PlayHandle.Video).watchUrl)
    }

    @Test
    fun `a podcast handle defaults to streaming, with no local file`() {
        assertNull(PlayHandle.Podcast().localPath)
        assertEquals("/tmp/ep.mp3", PlayHandle.Podcast("/tmp/ep.mp3").localPath)
    }

    @Test
    fun `a local video handle carries its path`() {
        assertEquals("/tmp/v.mkv", PlayHandle.LocalVideo("/tmp/v.mkv").localPath)
    }

    @Test
    fun `two playables differ when the same item is played a different way`() {
        val streamed = PlayableItem(item(), PlayHandle.Podcast())
        val downloaded = PlayableItem(item(), PlayHandle.Podcast("/tmp/ep.mp3"))
        assertNotEquals(streamed, downloaded)
        assertEquals(streamed, streamed.copy())
    }
}
