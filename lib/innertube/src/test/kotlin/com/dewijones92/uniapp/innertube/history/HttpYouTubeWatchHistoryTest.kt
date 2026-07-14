package com.dewijones92.uniapp.innertube.history

import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.OAuthTokens
import com.dewijones92.uniapp.innertube.auth.RefreshToken
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
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
        return HttpYouTubeWatchHistory(account, OkHttpClient(), newNonce = { "NONCE0123456789" })
    }

    private fun begin(history: HttpYouTubeWatchHistory, videoId: String = "vid1") {
        history.beginSession(
            videoId,
            playbackUrl = server.url("/api/stats/playback?docid=vid1&ei=E&len=600").toString(),
            watchtimeUrl = server.url("/api/stats/watchtime?docid=vid1&ei=E&len=600").toString(),
        )
    }

    private fun ok() = server.enqueue(MockResponse.Builder().code(204).build())

    @Test
    fun `first report opens the record then pings watchtime with cpn, position and bearer`() = runBlocking {
        ok() // playback (open record)
        ok() // watchtime
        val history = history()
        begin(history)

        assertEquals(WatchHistoryResult.Success, history.reportProgress("vid1", 30f, 600f, finished = false))

        val playback = server.takeRequest()
        assertTrue(playback.target.contains("/api/stats/playback"))
        assertTrue(playback.target.contains("cpn=NONCE0123456789"))
        assertTrue(playback.target.contains("cmt=30"))
        assertEquals("Bearer at", playback.headers["Authorization"])

        val watchtime = server.takeRequest()
        assertTrue(watchtime.target.contains("/api/stats/watchtime"))
        assertTrue(watchtime.target.contains("st=30"))
        assertTrue(watchtime.target.contains("et=30"))
        assertEquals("Bearer at", watchtime.headers["Authorization"])
    }

    @Test
    fun `a second report reuses the record and only pings watchtime`() = runBlocking {
        repeat(3) { ok() }
        val history = history()
        begin(history)

        history.reportProgress("vid1", 30f, 600f, finished = false)
        repeat(2) { server.takeRequest() } // playback + watchtime

        history.reportProgress("vid1", 60f, 600f, finished = false)
        val next = server.takeRequest()
        assertTrue(next.target.contains("/api/stats/watchtime"))
        assertTrue(next.target.contains("cmt=60"))
    }

    @Test
    fun `finishing marks final at full length`() = runBlocking {
        ok() // playback
        ok() // watchtime
        val history = history()
        begin(history)
        history.reportProgress("vid1", 595f, 600f, finished = true)
        assertTrue(server.takeRequest().target.contains("final=1")) // playback
        assertTrue(server.takeRequest().target.contains("cmt=600")) // watchtime at full length
    }

    @Test
    fun `no session means no request`() = runBlocking {
        assertEquals(WatchHistoryResult.NoSession, history().reportProgress("vid1", 30f, 600f, finished = false))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `signed out reports as a value without pinging`() = runBlocking {
        val history = history(signedIn = false)
        begin(history)
        assertEquals(WatchHistoryResult.SignedOut, history.reportProgress("vid1", 30f, 600f, finished = false))
        assertEquals(0, server.requestCount)
    }

    private companion object {
        val TOKENS = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3600)
    }
}
