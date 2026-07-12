package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePodcastRepositoryTest {

    private val repository = FakePodcastRepository()
    private val url = HttpUrl.of("https://podcast.example.com/feed.xml")

    @Test
    fun `subscribe adds a subscription and a sample episode`() = runTest {
        val result = repository.subscribe(url)

        assertTrue(result is SubscribeResult.Subscribed)
        assertEquals(1, repository.observeSubscriptions().first().size)
        assertEquals(1, repository.observeEpisodes().first().size)
    }

    @Test
    fun `subscribing twice reports AlreadySubscribed`() = runTest {
        repository.subscribe(url)

        assertEquals(
            SubscribeResult.AlreadySubscribed(SourceId(url.value)),
            repository.subscribe(url),
        )
    }

    @Test
    fun `unsubscribe removes the feed and its episodes`() = runTest {
        repository.subscribe(url)

        repository.unsubscribe(SourceId(url.value))

        assertTrue(repository.observeSubscriptions().first().isEmpty())
        assertTrue(repository.observeEpisodes().first().isEmpty())
    }
}
