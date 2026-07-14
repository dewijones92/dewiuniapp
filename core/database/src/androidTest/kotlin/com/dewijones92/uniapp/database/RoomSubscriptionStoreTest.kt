package com.dewijones92.uniapp.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RoomSubscriptionStoreTest {

    private lateinit var database: UniAppDatabase
    private lateinit var podcasts: RoomSubscriptionStore
    private lateinit var channels: RoomSubscriptionStore

    private val podcastSource = MediaSource.PodcastFeed(
        id = SourceId("feed-1"),
        title = "A podcast",
        feedUrl = HttpUrl.of("https://example.com/feed.xml"),
    )
    private val podcastSub = Subscription(podcastSource, subscribedAt = Instant.parse("2026-07-12T00:00:00Z"))
    private val episode = FakePodcastRepository.sampleEpisode(podcastSource.id)

    private val channelSource = MediaSource.VideoChannel(
        id = SourceId("chan-1"),
        title = "A channel",
        channelUrl = HttpUrl.of("https://example.com/@chan"),
    )
    private val channelSub = Subscription(channelSource, subscribedAt = Instant.parse("2026-07-12T00:00:00Z"))
    private val video = FakePodcastRepository.sampleEpisode(channelSource.id)

    @Before
    fun createStore() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UniAppDatabase::class.java,
        ).build()
        podcasts = RoomSubscriptionStore(database.podcastDao(), RoomSubscriptionStore.SourceType.PODCAST)
        channels = RoomSubscriptionStore(database.podcastDao(), RoomSubscriptionStore.SourceType.CHANNEL)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun sourceAndItemsRoundTrip() = runTest {
        podcasts.saveSource(podcastSub, listOf(episode))

        assertTrue(podcasts.contains(podcastSource.id))
        assertEquals(listOf(podcastSub), podcasts.observeSubscriptions().first())
        assertEquals(listOf(episode), podcasts.observeItems().first())
    }

    @Test
    fun removeSourceCascadesToItems() = runTest {
        podcasts.saveSource(podcastSub, listOf(episode))

        podcasts.removeSource(podcastSource.id)

        assertFalse(podcasts.contains(podcastSource.id))
        assertTrue(podcasts.observeItems().first().isEmpty())
    }

    @Test
    fun podcastAndChannelStoresAreIsolatedByType() = runTest {
        podcasts.saveSource(podcastSub, listOf(episode))
        channels.saveSource(channelSub, listOf(video))

        // Each typed store sees only its own pillar's sources and items.
        assertEquals(listOf(podcastSub), podcasts.observeSubscriptions().first())
        assertEquals(listOf(channelSub), channels.observeSubscriptions().first())
        assertEquals(listOf(episode), podcasts.observeItems().first())
        assertEquals(listOf(video), channels.observeItems().first())

        val roundTrippedChannel = channels.observeSubscriptions().first().single().source
        assertTrue(roundTrippedChannel is MediaSource.VideoChannel)
    }
}
