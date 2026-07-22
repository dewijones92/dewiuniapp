package com.dewijones92.uniapp.importexport

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.channel.fake.FakeChannelRepository
import com.dewijones92.uniapp.data.importexport.OpmlExporter
import com.dewijones92.uniapp.data.importexport.SubscriptionImportParser
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.innertube.actions.ActionResult
import com.dewijones92.uniapp.innertube.actions.fake.FakeYouTubeActions
import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.OAuthTokens
import com.dewijones92.uniapp.innertube.auth.RefreshToken
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.uniapp.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import com.dewijones92.uniapp.video.AccountSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionImporterTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private data class Harness(
        val importer: SubscriptionImporter,
        val channels: AccountSubscriptions,
        val actions: FakeYouTubeActions,
    )

    private fun harness(
        signedIn: Boolean = false,
        actionResult: ActionResult = ActionResult.Success,
        podcasts: FakePodcastRepository = FakePodcastRepository(),
    ): Harness {
        val account = YouTubeAccount(
            FakeYouTubeAuth(),
            InMemoryTokenStore(if (signedIn) TOKENS else null),
            nowEpochSeconds = { 0 },
        )
        val actions = FakeYouTubeActions(actionResult)
        val channels = AccountSubscriptions(FakeYouTubeSubscriptions(), actions, account, CoroutineScope(dispatcher))
        val importer = SubscriptionImporter(
            SubscriptionImportParser(),
            OpmlExporter(),
            podcasts,
            channels,
            FakeChannelRepository(),
        )
        return Harness(importer, channels, actions)
    }

    private fun podcastOpml(vararg feedUrls: String) = buildString {
        append("<opml version=\"2.0\"><body>")
        feedUrls.forEach { append("<outline type=\"rss\" text=\"Feed\" xmlUrl=\"$it\"/>") }
        append("</body></opml>")
    }

    private fun channelOpml(channelId: String) =
        "<opml version=\"2.0\"><body><outline type=\"rss\" text=\"Chan\" " +
            "xmlUrl=\"https://www.youtube.com/feeds/videos.xml?channel_id=$channelId\"/></body></opml>"

    @Test
    fun `adds podcasts, then reports them already-present on a second import`() = runTest {
        val h = harness()
        val opml = podcastOpml("https://feeds.example.com/a.xml", "https://feeds.example.com/b.xml")

        val first = h.importer.import(opml) as ImportOutcome.Done
        assertEquals(2, first.summary.podcastsAdded)

        val second = h.importer.import(opml) as ImportOutcome.Done
        assertEquals(0, second.summary.podcastsAdded)
        assertEquals(2, second.summary.alreadyPresent)
    }

    @Test
    fun `channels are skipped and counted while signed out`() = runTest {
        val h = harness(signedIn = false)

        val done = h.importer.import(channelOpml("UCaaaaaaaaaaaaaaaaaaaaaa")) as ImportOutcome.Done

        assertEquals(1, done.summary.channelsSkippedSignedOut)
        assertEquals(0, done.summary.channelsAdded)
        assertTrue(h.actions.subscribeCalls.isEmpty())
    }

    @Test
    fun `subscribes a channel to the account when signed in`() = runTest {
        val h = harness(signedIn = true)
        h.channels.refresh()
        advanceUntilIdle()

        val done = h.importer.import(channelOpml("UCaaaaaaaaaaaaaaaaaaaaaa")) as ImportOutcome.Done

        assertEquals(1, done.summary.channelsAdded)
        assertEquals("UCaaaaaaaaaaaaaaaaaaaaaa" to true, h.actions.subscribeCalls.single())
    }

    @Test
    fun `a failed account write counts as failed, not added`() = runTest {
        val h = harness(signedIn = true, actionResult = ActionResult.Failure("nope"))
        h.channels.refresh()
        advanceUntilIdle()

        val done = h.importer.import(channelOpml("UCaaaaaaaaaaaaaaaaaaaaaa")) as ImportOutcome.Done

        assertEquals(0, done.summary.channelsAdded)
        assertEquals(1, done.summary.failed)
    }

    @Test
    fun `a handle-based channel is resolved via the extractor before subscribing`() = runTest {
        val h = harness(signedIn = true)
        h.channels.refresh()
        advanceUntilIdle()
        val json = """{"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/@somehandle","name":"H"}]}"""

        val done = h.importer.import(json) as ImportOutcome.Done

        assertEquals(1, done.summary.channelsAdded)
        assertTrue(h.actions.subscribeCalls.single().first.startsWith("UC"))
    }

    @Test
    fun `a file with no recognisable subscriptions is a parse error`() = runTest {
        assertTrue(harness().importer.import("just some prose, no urls at all") is ImportOutcome.ParseError)
    }

    @Test
    fun `exportOpml includes the subscribed podcast feeds`() = runTest {
        val feed = "https://feeds.example.com/x.xml"
        val sub = Subscription(
            MediaSource.PodcastFeed(SourceId(feed), "X", HttpUrl.of(feed)),
            Instant.EPOCH,
        )
        val h = harness(podcasts = FakePodcastRepository(initialSubscriptions = listOf(sub)))

        assertTrue(h.importer.exportOpml().contains("xmlUrl=\"$feed\""))
    }

    private companion object {
        val TOKENS = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3_600)
    }
}
