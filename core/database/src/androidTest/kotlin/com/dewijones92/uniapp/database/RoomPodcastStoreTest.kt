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

class RoomPodcastStoreTest {

    private lateinit var database: UniAppDatabase
    private lateinit var store: RoomPodcastStore

    private val source = MediaSource.PodcastFeed(
        id = SourceId("feed-1"),
        title = "A podcast",
        feedUrl = HttpUrl.of("https://example.com/feed.xml"),
    )
    private val subscription = Subscription(source, subscribedAt = Instant.parse("2026-07-12T00:00:00Z"))
    private val episode = FakePodcastRepository.sampleEpisode(source.id)

    @Before
    fun createStore() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UniAppDatabase::class.java,
        ).build()
        store = RoomPodcastStore(database.podcastDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun feedAndEpisodesRoundTrip() = runTest {
        store.saveFeed(subscription, listOf(episode))

        assertTrue(store.contains(source.id))
        assertEquals(listOf(subscription), store.observeSubscriptions().first())
        assertEquals(listOf(episode), store.observeEpisodes().first())
    }

    @Test
    fun removeFeedCascadesToEpisodes() = runTest {
        store.saveFeed(subscription, listOf(episode))

        store.removeFeed(source.id)

        assertFalse(store.contains(source.id))
        assertTrue(store.observeEpisodes().first().isEmpty())
    }
}
