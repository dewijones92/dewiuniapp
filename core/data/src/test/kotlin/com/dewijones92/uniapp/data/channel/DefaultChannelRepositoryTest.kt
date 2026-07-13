package com.dewijones92.uniapp.data.channel

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.subscription.SubscriptionStore
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.ytdlp.ChannelResult
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DefaultChannelRepositoryTest {

    private val channelUrl = HttpUrl.of("https://www.youtube.com/@example")
    private val engine = FakeYtDlpEngine()
    private val store = InMemorySubscriptionStore()
    private val now = Instant.parse("2026-07-12T10:00:00Z")

    private fun repository() = DefaultChannelRepository(
        engine = engine,
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `subscribe stores channel and its videos`() = runTest {
        engine.registerChannel(
            channelUrl,
            ChannelResult.Success(
                channelId = "UC123",
                title = "Example Channel",
                videos = listOf(FakeYtDlpEngine.sampleSearchEntry(id = "v1", title = "First video")),
            ),
        )

        val result = repository().subscribe(channelUrl)

        val subscribed = result as SubscribeChannelResult.Subscribed
        assertEquals("Example Channel", subscribed.source.title)
        val stored = checkNotNull(store.saved)
        assertEquals(1, stored.second.size)
        assertEquals("v1", stored.second[0].id.value)
        // The watch URL is the stable handle stored as mediaUrl (resolved on play).
        assertEquals("https://example.com/watch?v=v1", stored.second[0].mediaUrl?.value)
    }

    @Test
    fun `not-a-channel is reported as a value`() = runTest {
        val result = repository().subscribe(channelUrl)
        assertTrue(result is SubscribeChannelResult.Failure.NotAChannel)
    }

    @Test
    fun `subscribing to an already-stored channel reports AlreadySubscribed`() = runTest {
        engine.registerChannel(
            channelUrl,
            ChannelResult.Success(channelId = "UC123", title = "Example", videos = emptyList()),
        )
        val repository = repository()

        repository.subscribe(channelUrl)
        assertEquals(
            SubscribeChannelResult.AlreadySubscribed(SourceId(channelUrl.value)),
            repository.subscribe(channelUrl),
        )
    }

    @Test
    fun `observers surface stored subscriptions and videos`() = runTest {
        engine.registerChannel(
            channelUrl,
            ChannelResult.Success(
                channelId = "UC123",
                title = "Example",
                videos = listOf(FakeYtDlpEngine.sampleSearchEntry(id = "v1")),
            ),
        )
        val repository = repository()
        repository.subscribe(channelUrl)

        assertEquals(1, repository.observeSubscriptions().first().size)
        assertEquals(1, repository.observeVideos().first().size)

        repository.unsubscribe(SourceId(channelUrl.value))
        assertTrue(repository.observeSubscriptions().first().isEmpty())
    }
}

private class InMemorySubscriptionStore : SubscriptionStore {
    var saved: Pair<Subscription, List<MediaItem>>? = null
    private val subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    private val items = MutableStateFlow<List<MediaItem>>(emptyList())

    override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptions
    override fun observeItems(): Flow<List<MediaItem>> = items
    override suspend fun contains(id: SourceId) = subscriptions.value.any { it.source.id == id }
    override suspend fun saveSource(subscription: Subscription, items: List<MediaItem>) {
        saved = subscription to items
        subscriptions.value += subscription
        this.items.value += items
    }
    override suspend fun removeSource(id: SourceId) {
        subscriptions.value = subscriptions.value.filterNot { it.source.id == id }
        items.value = items.value.filterNot { it.sourceId == id }
    }
}
