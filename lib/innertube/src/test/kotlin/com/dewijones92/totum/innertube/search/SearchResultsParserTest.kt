package com.dewijones92.totum.innertube.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses a real captured WEB search response (2026-07-25). */
class SearchResultsParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "fixture $name missing" }
            .bufferedReader().readText()

    private val videos = SearchResultsParser.videos(fixture("search_web_sample.json"))

    @Test
    fun `search results parse with an upload date on every hit`() {
        assertTrue("expected results", videos.size >= 10)
        // The date is the whole reason this parser exists: yt-dlp's flat search
        // results don't carry one, and the WEB search response does.
        assertEquals("every hit dated", videos.size, videos.count { it.publishedText != null })
    }

    @Test
    fun `each hit carries the fields the search row renders`() {
        assertTrue("titles", videos.all { it.title.isNotBlank() })
        assertTrue("authors", videos.all { !it.author.isNullOrBlank() })
        assertTrue("durations", videos.all { it.durationSeconds != null })
        assertTrue("thumbnails", videos.all { it.thumbnailUrl != null })
        assertTrue(
            "watch urls built from the video id",
            videos.all { it.watchUrl.value == "https://www.youtube.com/watch?v=${it.videoId}" },
        )
    }

    @Test
    fun `results are de-duplicated in relevance order`() {
        assertEquals(videos.map { it.videoId }.distinct(), videos.map { it.videoId })
    }

    @Test
    fun `a malformed body yields no results rather than throwing`() {
        assertTrue(SearchResultsParser.videos("not json").isEmpty())
        assertTrue(SearchResultsParser.videos("{}").isEmpty())
    }
}
