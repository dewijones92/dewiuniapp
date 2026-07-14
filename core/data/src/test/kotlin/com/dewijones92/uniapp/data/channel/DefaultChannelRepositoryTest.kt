package com.dewijones92.uniapp.data.channel

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.ChannelResult
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultChannelRepositoryTest {

    private val channelUrl = HttpUrl.of("https://www.youtube.com/@example")
    private val engine = FakeYtDlpEngine()

    private fun repository() = DefaultChannelRepository(engine = engine)

    @Test
    fun `fetchChannelVideos returns the channel's id, title and uploads`() = runTest {
        engine.registerChannel(
            channelUrl,
            ChannelResult.Success(
                channelId = "UC123",
                title = "Example Channel",
                videos = listOf(FakeYtDlpEngine.sampleSearchEntry(id = "v1", title = "First video")),
            ),
        )

        val result = repository().fetchChannelVideos(channelUrl) as ChannelVideosResult.Success

        assertEquals("UC123", result.channelId)
        assertEquals("Example Channel", result.title)
        assertEquals(1, result.videos.size)
        assertEquals("v1", result.videos[0].id.value)
        // The watch URL is the stable handle stored as mediaUrl (resolved on play).
        assertEquals("https://example.com/watch?v=v1", result.videos[0].mediaUrl?.value)
    }

    @Test
    fun `fetchChannelVideos reports not-a-channel as a value`() = runTest {
        val result = repository().fetchChannelVideos(channelUrl)
        assertTrue(result is ChannelVideosResult.Failure.NotAChannel)
    }
}
