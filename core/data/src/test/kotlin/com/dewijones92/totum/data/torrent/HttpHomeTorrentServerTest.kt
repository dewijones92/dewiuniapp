package com.dewijones92.totum.data.torrent

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Credentials are read at REQUEST time, never captured at construction.
 *
 * This is the bug from report 0.1.313 and it hid for a day. Signing in stored both the token and
 * Prowlarr's key, the app logged `token=true prowlarrKey=true`, and every search still failed
 * with 401 twelve seconds later — because the client had been built before sign-in and kept the
 * empty key it was born with. Prowlarr answers a bad key with 401, which reads exactly like the
 * gate refusing the token, so the message pointed at the wrong half of the system.
 */
class HttpHomeTorrentServerTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun serverWith(token: () -> String, key: () -> String) = HttpHomeTorrentServer(
        client = OkHttpClient(),
        base = server.url("").toString().trimEnd('/'),
        prowlarrApiKey = key,
        token = token,
    )

    private fun enqueueEmptyResults() =
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())

    @Test
    fun `a key stored after construction is the one actually sent`() = runTest {
        var key = ""
        val torrents = serverWith(token = { "tok" }, key = { key })

        // Sign-in happens AFTER the client exists — the ordering on a fresh install.
        key = "the-real-key"
        enqueueEmptyResults()
        torrents.search("peep show")

        assertEquals("the-real-key", server.takeRequest().headers["X-Api-Key"])
    }

    @Test
    fun `a token stored after construction is the one actually sent`() = runTest {
        var token = ""
        val torrents = serverWith(token = { token }, key = { "k" })

        token = "the-real-token"
        enqueueEmptyResults()
        torrents.search("peep show")

        assertEquals("the-real-token", server.takeRequest().headers["X-Totum-Token"])
    }

    /** Not signed in is answered without a request at all — there is nothing to ask. */
    @Test
    fun `a blank token does not even reach the network`() = runTest {
        val result = serverWith(token = { "" }, key = { "k" }).search("peep show")

        assertEquals(0, server.requestCount)
        assertTrue(result is TorrentSearchResult.Failure)
    }

    /** 401 says which half is wrong in words, because the number cannot. */
    @Test
    fun `a rejection is reported as something a person can act on`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())

        val result = serverWith(token = { "t" }, key = { "k" }).search("peep show")

        val detail = (result as TorrentSearchResult.Failure).detail
        assertTrue("should tell them to sign in again, was: $detail", detail.contains("sign in again"))
    }

    /** A slow fan-out that times out at the gateway is not the same as a broken server. */
    @Test
    fun `a gateway timeout suggests narrowing the search`() = runTest {
        server.enqueue(MockResponse.Builder().code(504).build())

        val result = serverWith(token = { "t" }, key = { "k" }).search("peep show")

        val detail = (result as TorrentSearchResult.Failure).detail
        assertTrue("should mention it took too long, was: $detail", detail.contains("too long"))
    }
}
