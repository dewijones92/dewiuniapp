package com.dewijones92.uniapp.data.source

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.SubscribeResult
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.ytdlp.MediaMetadata
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLocatorTest {

    private val engine = FakeYtDlpEngine()
    private val podcasts = FakePodcastRepository()
    private val locator = DefaultSourceLocator(podcasts, engine)

    private val watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=abc123")

    private fun video(sourceId: String) = MediaItem(
        id = MediaItemId("abc123"),
        sourceId = SourceId(sourceId),
        title = "A video",
        publishedAt = null,
        duration = null,
        author = "Some Channel",
        mediaUrl = watchUrl,
    )

    private fun metadata(uploaderUrl: String?) = MediaMetadata(
        id = "abc123",
        title = "A video",
        uploader = "Some Channel",
        durationSeconds = null,
        thumbnailUrl = null,
        formats = emptyList(),
        uploaderUrl = uploaderUrl,
    )

    @Test
    fun `a subscribed podcast episode locates its feed without touching the engine`() = runTest {
        val feedUrl = HttpUrl.of("https://example.com/feed.xml")
        val subscribed = podcasts.subscribe(feedUrl) as SubscribeResult.Subscribed
        val feed = subscribed.source
        val episode = podcasts.observeEpisodes().first().single()

        val located = locator.locate(episode)

        assertTrue(located is MediaSource.PodcastFeed)
        assertEquals(feed.id, located?.id)
    }

    @Test
    fun `a video locates its uploader's channel through the engine`() = runTest {
        engine.registerMedia(watchUrl, metadata("https://www.youtube.com/channel/UCxyz"))

        val located = locator.locate(video("ytfeed:SUBSCRIPTIONS"))

        assertTrue(located is MediaSource.VideoChannel)
        assertEquals("https://www.youtube.com/channel/UCxyz", (located as MediaSource.VideoChannel).channelUrl.value)
        assertEquals("Some Channel", located.title)
    }

    @Test
    fun `a video whose extraction reports no uploader page locates nothing`() = runTest {
        engine.registerMedia(watchUrl, metadata(uploaderUrl = null))

        assertNull(locator.locate(video("ytfeed:SUBSCRIPTIONS")))
    }

    @Test
    fun `an unresolvable item locates nothing`() = runTest {
        assertNull(locator.locate(video("ytfeed:SUBSCRIPTIONS")))
    }
}
