package com.dewijones92.uniapp.data.content

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PodcastSubscriptionItemsSourceTest {

    private fun feed(id: String) =
        MediaSource.PodcastFeed(SourceId(id), id, HttpUrl.of("https://example.com/$id.xml"))

    private fun episode(id: String, source: String) = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId(source),
        title = id,
        publishedAt = Instant.EPOCH,
        duration = null,
    )

    @Test
    fun `groups each feed's episodes under its source, including feeds with none`() = runTest {
        val repository = FakePodcastRepository(
            initialSubscriptions = listOf(
                Subscription(feed("a"), Instant.EPOCH),
                Subscription(feed("b"), Instant.EPOCH),
            ),
            initialEpisodes = listOf(episode("a1", "a"), episode("a2", "a"), episode("b1", "b")),
        )

        val updates = PodcastSubscriptionItemsSource(repository).currentItems()

        assertEquals(2, updates.size)
        val byId = updates.associateBy { it.source.id.value }
        assertEquals(listOf("a1", "a2"), byId.getValue("a").items.map { it.id.value })
        assertEquals(listOf("b1"), byId.getValue("b").items.map { it.id.value })
    }
}
