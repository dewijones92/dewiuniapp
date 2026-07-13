package com.dewijones92.uniapp.innertube.comments

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

class HttpYouTubeCommentsTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun comments(): HttpYouTubeComments {
        val innerTube = InnerTubeClient(OkHttpClient(), nextUrl = server.url("/next").toString())
        return HttpYouTubeComments(innerTube)
    }

    private fun res(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "fixture $name missing" }
            .bufferedReader().readText()

    @Test
    fun `two-step fetch returns parsed comments`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(res("comments_section_sample.json")).build())
        server.enqueue(MockResponse.Builder().code(200).body(res("comments_page_sample.json")).build())

        val result = comments().forVideo("vid12345678")

        assertEquals(listOf("c1", "c2"), (result as CommentsResult.Success).comments.map { it.id })
        assertTrue(server.takeRequest().body?.utf8().orEmpty().contains("vid12345678"))
        assertTrue(server.takeRequest().body?.utf8().orEmpty().contains("COMMENT_TOKEN_123456789012"))
    }

    @Test
    fun `no comment section means comments are disabled`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("""{"contents":{}}""").build())
        assertEquals(CommentsResult.Disabled, comments().forVideo("v"))
    }

    @Test
    fun `a server error on the watch page is a failure`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("").build())
        assertTrue(comments().forVideo("v") is CommentsResult.Failure)
    }
}
