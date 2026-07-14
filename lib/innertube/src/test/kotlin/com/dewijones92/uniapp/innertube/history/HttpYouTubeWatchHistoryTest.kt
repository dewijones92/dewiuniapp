package com.dewijones92.uniapp.innertube.history

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

class HttpYouTubeWatchHistoryTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun history(signedIn: Boolean = true): HttpYouTubeWatchHistory {
        val initial = if (signedIn) TOKENS else null
        val account = YouTubeAccount(FakeYouTubeAuth(), InMemoryTokenStore(initial), nowEpochSeconds = { 0 })
        return HttpYouTubeWatchHistory(
            account = account,
            innerTube = InnerTubeClient(OkHttpClient(), playerUrl = server.url("/player").toString()),
            newNonce = { "NONCE0123456789" },
        )
    }

    private fun playerResponse() {
        val playback = server.url("/api/stats/playback?docid=vid1&ei=E&of=O&vm=V&len=600").toString()
        val watchtime = server.url("/api/stats/watchtime?docid=vid1&ei=E&of=O&vm=V&len=600").toString()
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"playbackTracking":{
                    "videostatsPlaybackUrl":{"baseUrl":"$playback"},
                    "videostatsWatchtimeUrl":{"baseUrl":"$watchtime"}
                }}""",
            ).build(),
        )
    }

    private fun ok() = server.enqueue(MockResponse.Builder().code(204).build())

    @Test
    fun `first report opens the record then pings watchtime, both with cpn and position`() = runBlocking {
        playerResponse()
        ok() // playback (create record)
        ok() // watchtime

        assertEquals(
            WatchHistoryResult.Success,
            history().reportProgress("vid1", positionSec = 30f, lengthSec = 600f, finished = false),
        )

        val player = server.takeRequest()
        assertEquals("Bearer at", player.headers["Authorization"])
        assertTrue(player.body?.utf8().orEmpty().contains(""""videoId":"vid1""""))

        val playback = server.takeRequest()
        assertTrue(playback.target.contains("/api/stats/playback"))
        assertTrue(playback.target.contains("cpn=NONCE0123456789"))
        assertTrue(playback.target.contains("cmt=30"))
        assertEquals("Bearer at", playback.headers["Authorization"])

        val watchtime = server.takeRequest()
        assertTrue(watchtime.target.contains("/api/stats/watchtime"))
        assertTrue(watchtime.target.contains("st=30"))
        assertTrue(watchtime.target.contains("et=30"))
    }

    @Test
    fun `a second report reuses the record and only pings watchtime`() = runBlocking {
        playerResponse()
        ok() // first report: playback (create record)
        ok() // first report: watchtime
        ok() // second report: watchtime only
        val history = history()

        history.reportProgress("vid1", 30f, 600f, finished = false)
        repeat(3) { server.takeRequest() } // drain player + playback + watchtime

        history.reportProgress("vid1", 60f, 600f, finished = false)
        val next = server.takeRequest()
        assertTrue(next.target.contains("/api/stats/watchtime"))
        assertTrue(next.target.contains("cmt=60"))
    }

    @Test
    fun `finishing marks final at full length`() = runBlocking {
        playerResponse()
        ok() // playback
        ok() // watchtime
        history().reportProgress("vid1", 595f, 600f, finished = true)
        server.takeRequest() // player
        assertTrue(server.takeRequest().target.contains("final=1")) // playback
        assertTrue(server.takeRequest().target.contains("cmt=600")) // watchtime at full length
    }

    @Test
    fun `signed out reports as a value without any request`() = runBlocking {
        assertEquals(
            WatchHistoryResult.SignedOut,
            history(signedIn = false).reportProgress("vid1", 30f, 600f, finished = false),
        )
        assertEquals(0, server.requestCount)
    }

    private companion object {
        val TOKENS = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3600)
    }
}
