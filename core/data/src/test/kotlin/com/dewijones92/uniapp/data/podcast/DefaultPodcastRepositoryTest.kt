package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.net.FetchResult
import com.dewijones92.uniapp.data.subscription.SubscriptionStore
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DefaultPodcastRepositoryTest {

    private val feedUrl = HttpUrl.of("https://podcast.example.com/feed.xml")
    private val store = InMemoryPodcastStore()
    private val now = Instant.parse("2026-07-12T10:00:00Z")

    private fun repository(fetchResult: FetchResult) = DefaultPodcastRepository(
        fetcher = { fetchResult },
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `subscribe stores feed and episodes`() = runTest {
        val xml = checkNotNull(javaClass.getResource("/sample-feed.xml")).readText()
        val result = repository(FetchResult.Success(xml)).subscribe(feedUrl)

        val subscribed = result as SubscribeResult.Subscribed
        assertEquals("The Test Podcast", subscribed.source.title)
        assertEquals(feedUrl, subscribed.source.feedUrl)

        val stored = checkNotNull(store.saved)
        assertEquals(now, stored.first.subscribedAt)
        assertEquals(3, stored.second.size)
        assertEquals("ep-2-guid", stored.second[0].id.value)
        // No guid -> enclosure URL becomes the stable id.
        assertEquals("https://cdn.example.com/ep1.mp3", stored.second[1].id.value)
        // No guid or enclosure -> positional fallback.
        assertEquals("${feedUrl.value}#2", stored.second[2].id.value)
        assertEquals("https://cdn.example.com/ep2.mp3", stored.second[0].mediaUrl?.value)
        // Episode author when present; feed title as the fallback.
        assertEquals("A Guest Author", stored.second[0].author)
        assertEquals("The Test Podcast", stored.second[1].author)
    }

    @Test
    fun `subscribing twice reports AlreadySubscribed`() = runTest {
        val xml = checkNotNull(javaClass.getResource("/sample-feed.xml")).readText()
        val repository = repository(FetchResult.Success(xml))

        repository.subscribe(feedUrl)
        assertEquals(
            SubscribeResult.AlreadySubscribed(SourceId(feedUrl.value)),
            repository.subscribe(feedUrl),
        )
    }

    @Test
    fun `network failure is reported as a value`() = runTest {
        val result = repository(FetchResult.Failure("HTTP 503")).subscribe(feedUrl)
        assertEquals(SubscribeResult.Failure.Network("HTTP 503"), result)
    }

    @Test
    fun `unparseable body is reported as InvalidFeed`() = runTest {
        val result = repository(FetchResult.Success("not a feed")).subscribe(feedUrl)
        assertTrue(result is SubscribeResult.Failure.InvalidFeed)
    }

    @Test
    fun `unsubscribe removes the feed from the store`() = runTest {
        val xml = checkNotNull(javaClass.getResource("/sample-feed.xml")).readText()
        val repository = repository(FetchResult.Success(xml))
        repository.subscribe(feedUrl)

        repository.unsubscribe(SourceId(feedUrl.value))
        assertTrue(store.removed.contains(SourceId(feedUrl.value)))
    }
}

private class InMemoryPodcastStore : SubscriptionStore {
    var saved: Pair<Subscription, List<MediaItem>>? = null
    val removed = mutableListOf<SourceId>()

    private val subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    private val episodes = MutableStateFlow<List<MediaItem>>(emptyList())

    override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptions
    override fun observeItems(): Flow<List<MediaItem>> = episodes
    override suspend fun contains(id: SourceId): Boolean =
        subscriptions.value.any { it.source.id == id }

    override suspend fun saveSource(subscription: Subscription, items: List<MediaItem>) {
        saved = subscription to items
        subscriptions.value += subscription
    }

    override suspend fun removeSource(id: SourceId) {
        removed += id
        subscriptions.value = subscriptions.value.filterNot { it.source.id == id }
    }
}
