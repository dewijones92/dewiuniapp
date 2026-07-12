package com.dewijones92.uniapp.domain

import com.dewijones92.uniapp.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MediaSourceTest {

    private val feed = MediaSource.PodcastFeed(
        id = SourceId("feed-1"),
        title = "A podcast",
        feedUrl = HttpUrl.of("https://example.com/feed.xml"),
    )

    private val channel = MediaSource.VideoChannel(
        id = SourceId("channel-1"),
        title = "A channel",
        channelUrl = HttpUrl.of("https://example.com/@channel"),
    )

    @Test
    fun `podcast feed website is optional`() {
        assertNull(feed.websiteUrl)
    }

    @Test
    fun `sources with the same data are equal`() {
        assertEquals(feed, feed.copy())
        assertEquals(channel, channel.copy())
    }

    @Test
    fun `sources with different ids are not equal`() {
        assertNotEquals(feed, feed.copy(id = SourceId("feed-2")))
    }

    @Test
    fun `both pillars are handled exhaustively as MediaSource`() {
        val titles = listOf<MediaSource>(feed, channel).map { source ->
            when (source) {
                is MediaSource.PodcastFeed -> "podcast:${source.title}"
                is MediaSource.VideoChannel -> "video:${source.title}"
            }
        }
        assertEquals(listOf("podcast:A podcast", "video:A channel"), titles)
    }

    @Test
    fun `subscription carries its source and time`() {
        val at = Instant.parse("2026-07-12T00:00:00Z")
        val subscription = Subscription(source = feed, subscribedAt = at)
        assertEquals(feed, subscription.source)
        assertEquals(at, subscription.subscribedAt)
    }
}
