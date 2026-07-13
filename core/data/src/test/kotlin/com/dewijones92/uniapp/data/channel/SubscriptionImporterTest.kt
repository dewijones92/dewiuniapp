package com.dewijones92.uniapp.data.channel

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.channel.fake.FakeChannelRepository
import com.dewijones92.uniapp.innertube.subscriptions.SubscribedChannel
import com.dewijones92.uniapp.innertube.subscriptions.SubscriptionsResult
import com.dewijones92.uniapp.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionImporterTest {

    private val channels = FakeChannelRepository()

    private fun channel(id: String, title: String) = SubscribedChannel(
        channelId = id,
        title = title,
        channelUrl = HttpUrl.of("https://www.youtube.com/channel/$id"),
        avatarUrl = null,
    )

    @Test
    fun `imports fetched channels into the shared subscription store`() = runTest {
        val subs = FakeYouTubeSubscriptions(
            SubscriptionsResult.Success(listOf(channel("UC1", "One"), channel("UC2", "Two"))),
        )

        val result = SubscriptionImporter(subs, channels).import()

        assertEquals(ImportResult.Imported(added = 2, total = 2), result)
        val stored = channels.observeSubscriptions().first()
        assertEquals(listOf("One", "Two"), stored.map { it.source.title })
    }

    @Test
    fun `already-subscribed channels are not re-added`() = runTest {
        channels.subscribe(HttpUrl.of("https://www.youtube.com/channel/UC1"))
        val subs = FakeYouTubeSubscriptions(
            SubscriptionsResult.Success(listOf(channel("UC1", "One"), channel("UC2", "Two"))),
        )

        val result = SubscriptionImporter(subs, channels).import()

        // UC1 already present -> only UC2 is new, but total reflects the full fetch.
        assertEquals(ImportResult.Imported(added = 1, total = 2), result)
    }

    @Test
    fun `signed out is propagated`() = runTest {
        val subs = FakeYouTubeSubscriptions(SubscriptionsResult.SignedOut)
        assertEquals(ImportResult.SignedOut, SubscriptionImporter(subs, channels).import())
    }

    @Test
    fun `a fetch failure is surfaced`() = runTest {
        val subs = FakeYouTubeSubscriptions(SubscriptionsResult.Failure("offline"))
        assertTrue(SubscriptionImporter(subs, channels).import() is ImportResult.Failure)
    }
}
