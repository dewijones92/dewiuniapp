package com.dewijones92.totum.innertube.channel

import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.innertube.browse.InnerTubeClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Channel tabs, paged. These assert the two things that were silently broken before: the
 * response's continuation reaches the caller, and asking for a later page sends the
 * continuation instead of the tab params.
 */
class HttpYouTubeChannelPagingTest {

    private val server = MockWebServer()
    private lateinit var channel: HttpYouTubeChannel

    @Before
    fun start() {
        server.start()
        channel = HttpYouTubeChannel(
            InnerTubeClient(
                client = OkHttpClient(),
                browseUrl = server.url("/browse").toString(),
                nextUrl = server.url("/next").toString(),
                searchUrl = server.url("/search").toString(),
            ),
        )
    }

    @After
    fun stop() = server.close()

    private fun body(videoId: String, continuation: String?) = buildString {
        append("""{"contents":{"items":[""")
        append(
            """{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"$videoId",""" +
                """"metadata":{"lockupMetadataViewModel":{"title":{"content":"Video $videoId"}}}}}""",
        )
        append("]")
        if (continuation != null) {
            append(
                ""","footer":{"continuationItemRenderer":{"continuationEndpoint":""" +
                    """{"continuationCommand":{"token":"$continuation"}}}}""",
            )
        }
        append("}}")
    }

    @Test
    fun `a tab with a continuation reports more to come`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(body("aaaaaaaaaaa", "chan-page-2")).build())

        val result = channel.videos("UC123") as ChannelVideos.Success

        assertTrue(result.page.hasMore)
        assertEquals(PageToken("chan-page-2"), result.page.next)
    }

    @Test
    fun `a tab without a continuation is the last page`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(body("aaaaaaaaaaa", null)).build())

        val result = channel.videos("UC123") as ChannelVideos.Success

        assertFalse(result.page.hasMore)
    }

    /**
     * The continuation replaces the tab params rather than accompanying them — it already
     * encodes which tab it continues, and sending both is meaningless.
     */
    @Test
    fun `a later page sends the continuation and not the tab params`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(body("bbbbbbbbbbb", null)).build())

        channel.videos("UC123", after = PageToken("chan-page-2"))

        val sent = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(sent, sent.contains("\"continuation\":\"chan-page-2\""))
        assertFalse(sent, sent.contains("\"params\""))
    }

    @Test
    fun `a first page sends the tab params and no continuation`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(body("aaaaaaaaaaa", null)).build())

        channel.videos("UC123")

        val sent = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(sent, sent.contains("\"browseId\":\"UC123\""))
        assertTrue(sent, sent.contains("\"params\""))
        assertFalse(sent, sent.contains("\"continuation\""))
    }

    /** An unreadable response must end the tab rather than offer a page that can't load. */
    @Test
    fun `an empty page carries no continuation even when the response has one`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(
                """{"contents":{"continuationItemRenderer":""" +
                    """{"continuationEndpoint":{"continuationCommand":{"token":"orphan"}}}}}""",
            ).build(),
        )

        val result = channel.videos("UC123") as ChannelVideos.Success

        assertTrue(result.page.items.isEmpty())
        assertNull(result.page.next)
    }

    @Test
    fun `shorts and playlists page through the same path`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(body("aaaaaaaaaaa", null)).build())
        channel.shorts("UC123", after = PageToken("shorts-2"))
        assertTrue(server.takeRequest().body?.utf8().orEmpty().contains("\"continuation\":\"shorts-2\""))

        server.enqueue(MockResponse.Builder().body(body("aaaaaaaaaaa", null)).build())
        channel.playlists("UC123", after = PageToken("lists-2"))
        assertTrue(server.takeRequest().body?.utf8().orEmpty().contains("\"continuation\":\"lists-2\""))
    }
}
