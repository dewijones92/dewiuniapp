package com.dewijones92.uniapp.data.search

import com.dewijones92.uniapp.data.net.FetchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItunesPodcastSearchSourceTest {

    private val query = SearchQuery("in our time")

    @Test
    fun `maps directory results to podcast hits`() = runTest {
        val body = """
            {"resultCount": 3, "results": [
                {"collectionName": "In Our Time", "artistName": "BBC",
                 "feedUrl": "https://podcasts.files.bbci.co.uk/b006qykl.rss",
                 "artworkUrl600": "https://is1.example.com/art.jpg"},
                {"collectionName": "No feed url podcast", "artistName": "X"},
                {"artistName": "No title", "feedUrl": "https://example.com/feed.xml"}
            ]}
        """.trimIndent()
        val source = ItunesPodcastSearchSource { FetchResult.Success(body) }

        val hits = (source.search(query, limit = 10) as SearchOutcome.Success).hits

        assertEquals(1, hits.size)
        val hit = hits[0] as SearchHit.Podcast
        assertEquals("In Our Time", hit.title)
        assertEquals("BBC", hit.subtitle)
        assertEquals("https://podcasts.files.bbci.co.uk/b006qykl.rss", hit.feedUrl.value)
    }

    @Test
    fun `network failure is a value`() = runTest {
        val source = ItunesPodcastSearchSource { FetchResult.Failure("HTTP 503") }
        assertEquals(SearchOutcome.Failure("HTTP 503"), source.search(query, limit = 10))
    }

    @Test
    fun `unparseable body is a failure value`() = runTest {
        val source = ItunesPodcastSearchSource { FetchResult.Success("<html>not json</html>") }
        assertTrue(source.search(query, limit = 10) is SearchOutcome.Failure)
    }

    @Test
    fun `blank query is unrepresentable`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { SearchQuery("  ") }
    }
}
