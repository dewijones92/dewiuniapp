package com.dewijones92.totum.innertube.subscriptions

import com.dewijones92.totum.innertube.auth.AccessToken
import com.dewijones92.totum.innertube.auth.OAuthTokens
import com.dewijones92.totum.innertube.auth.RefreshToken
import com.dewijones92.totum.innertube.auth.YouTubeAccount
import com.dewijones92.totum.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.totum.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.totum.innertube.browse.InnerTubeClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpYouTubeSubscriptionsTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun subscriptions(signedIn: Boolean = true): HttpYouTubeSubscriptions {
        val initial = if (signedIn) TOKENS else null
        val account = YouTubeAccount(FakeYouTubeAuth(), InMemoryTokenStore(initial), nowEpochSeconds = { 0 })
        val innerTube = InnerTubeClient(OkHttpClient(), browseUrl = server.url("/browse").toString())
        return HttpYouTubeSubscriptions(account, innerTube)
    }

    @Test
    fun `sends a bearer token and parses the channels`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"contents":{"tvBrowseRenderer":{"content":{"items":[
                   {"tileRenderer":{"contentType":"TILE_CONTENT_TYPE_CHANNEL",
                    "onSelectCommand":{"browseEndpoint":{"browseId":"UCzzz"}},
                    "metadata":{"tileMetadataRenderer":{"title":{"simpleText":"Zed"}}}}}]}}}}""",
            ).build(),
        )

        val result = subscriptions().list()

        assertEquals("UCzzz", (result as SubscriptionsResult.Success).channels.single().channelId)
        assertEquals("Bearer at", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `signed out when there is no token`() = runBlocking {
        assertEquals(SubscriptionsResult.SignedOut, subscriptions(signedIn = false).list())
    }

    @Test
    fun `a 403 from InnerTube is treated as signed out`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(403).body("nope").build())
        assertEquals(SubscriptionsResult.SignedOut, subscriptions().list())
    }

    @Test
    fun `a server error is a failure value`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("").build())
        assertTrue(subscriptions().list() is SubscriptionsResult.Failure)
    }

    @Test
    fun `follows continuations to the end of the grid`() = runBlocking {
        server.enqueue(page("UCone", "One", continuation = "page2"))
        server.enqueue(page("UCtwo", "Two", continuation = "page3"))
        server.enqueue(page("UCthree", "Three", continuation = null))

        val result = subscriptions().list()

        assertEquals(
            listOf("UCone", "UCtwo", "UCthree"),
            (result as SubscriptionsResult.Success).channels.map { it.channelId },
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a channel seen on both pages is listed once`() = runBlocking {
        server.enqueue(page("UCone", "One", continuation = "page2"))
        server.enqueue(page("UCone", "One", continuation = null))

        val result = subscriptions().list()

        assertEquals(1, (result as SubscriptionsResult.Success).channels.size)
    }

    @Test
    fun `keeps the pages that worked when a later one fails`() = runBlocking {
        server.enqueue(page("UCone", "One", continuation = "page2"))
        server.enqueue(MockResponse.Builder().code(500).body("").build())

        val result = subscriptions().list()

        assertEquals("UCone", (result as SubscriptionsResult.Success).channels.single().channelId)
    }

    private fun page(channelId: String, title: String, continuation: String?): MockResponse {
        val tile = """{"tileRenderer":{"contentType":"TILE_CONTENT_TYPE_CHANNEL",""" +
            """"onSelectCommand":{"browseEndpoint":{"browseId":"$channelId"}},""" +
            """"metadata":{"tileMetadataRenderer":{"title":{"simpleText":"$title"}}}}}"""
        val more = continuation?.let {
            ""","continuations":[{"nextContinuationData":{"continuation":"$it"}}]"""
        }.orEmpty()
        return MockResponse.Builder().code(200)
            .body("""{"contents":{"tvBrowseRenderer":{"content":{"items":[$tile]$more}}}}""")
            .build()
    }

    private companion object {
        val TOKENS = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3_600)
    }
}
