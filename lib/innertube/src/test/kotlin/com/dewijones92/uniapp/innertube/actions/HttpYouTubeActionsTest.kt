package com.dewijones92.uniapp.innertube.actions

import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.OAuthTokens
import com.dewijones92.uniapp.innertube.auth.RefreshToken
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpYouTubeActionsTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun actions(signedIn: Boolean = true): HttpYouTubeActions {
        val initial = if (signedIn) TOKENS else null
        val account = YouTubeAccount(FakeYouTubeAuth(), InMemoryTokenStore(initial), nowEpochSeconds = { 0 })
        val mock = server.url("/act").toString()
        return HttpYouTubeActions(
            account = account,
            innerTube = InnerTubeClient(OkHttpClient()),
            endpoints = HttpYouTubeActions.ActionEndpoints(
                subscribe = mock,
                unsubscribe = mock,
                like = mock,
                dislike = server.url("/act/dislike").toString(),
                removeLike = mock,
                createComment = mock,
            ),
        )
    }

    private fun ok() = server.enqueue(
        MockResponse.Builder().code(200).body("""{"actionResult":{"status":"STATUS_SUCCEEDED"}}""").build(),
    )

    @Test
    fun `like posts the video target with a bearer token`() = runBlocking {
        ok()
        assertEquals(ActionResult.Success, actions().setRating("vid12345678", VideoRating.LIKE))
        val request = server.takeRequest()
        assertEquals("Bearer at", request.headers["Authorization"])
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""videoId":"vid12345678""""))
        assertTrue(body.contains("TVHTML5"))
    }

    @Test
    fun `dislike posts to the dislike endpoint`() = runBlocking {
        ok()
        assertEquals(ActionResult.Success, actions().setRating("vid12345678", VideoRating.DISLIKE))
        assertTrue(server.takeRequest().target.contains("/act/dislike"))
    }

    @Test
    fun `subscribe and unsubscribe carry the channel id`() = runBlocking {
        ok()
        assertEquals(ActionResult.Success, actions().setSubscribed("UCabc", subscribed = true))
        assertTrue(server.takeRequest().body?.utf8().orEmpty().contains(""""channelIds":["UCabc"]"""))
    }

    @Test
    fun `posting a comment sends built params and escaped text`() = runBlocking {
        ok()
        assertEquals(ActionResult.Success, actions().postComment("vid12345678", """he said "hi"\ok"""))
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("createCommentParams"))
        // Quotes and backslash in the text are escaped, so the JSON stays valid.
        assertTrue(body.contains("""he said \"hi\""""))
    }

    @Test
    fun `signed out short-circuits without a request`() = runBlocking {
        assertEquals(ActionResult.SignedOut, actions(signedIn = false).setRating("v", VideoRating.LIKE))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a server error is a failure`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("").build())
        assertTrue(actions().setSubscribed("UCabc", subscribed = false) is ActionResult.Failure)
    }

    private companion object {
        val TOKENS = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3_600)
    }
}
