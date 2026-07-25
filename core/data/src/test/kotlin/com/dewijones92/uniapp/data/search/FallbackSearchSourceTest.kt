package com.dewijones92.uniapp.data.search

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackSearchSourceTest {

    private val query = SearchQuery("kotlin")

    private fun hit(title: String) = SearchHit.Video(
        title = title,
        subtitle = null,
        artworkUrl = null,
        watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=$title"),
        durationSeconds = null,
    )

    private fun source(outcome: SearchOutcome) = SearchSource { _, _ -> outcome }

    private fun titles(outcome: SearchOutcome) =
        (outcome as SearchOutcome.Success).hits.map { it.title }

    @Test
    fun `primary hits are used and the fallback is never consulted`() = runTest {
        var fallbackCalled = false
        val fallback = SearchSource { _, _ ->
            fallbackCalled = true
            SearchOutcome.Success(listOf(hit("from-fallback")))
        }

        val outcome = FallbackSearchSource(source(SearchOutcome.Success(listOf(hit("from-primary")))), fallback)
            .search(query, 10)

        assertEquals(listOf("from-primary"), titles(outcome))
        assertTrue("fallback must not run when the primary answered", !fallbackCalled)
    }

    @Test
    fun `a primary failure falls back`() = runTest {
        val outcome = FallbackSearchSource(
            source(SearchOutcome.Failure("boom")),
            source(SearchOutcome.Success(listOf(hit("from-fallback")))),
        ).search(query, 10)

        assertEquals(listOf("from-fallback"), titles(outcome))
    }

    @Test
    fun `an empty primary result falls back too`() = runTest {
        val outcome = FallbackSearchSource(
            source(SearchOutcome.Success(emptyList())),
            source(SearchOutcome.Success(listOf(hit("from-fallback")))),
        ).search(query, 10)

        assertEquals(listOf("from-fallback"), titles(outcome))
    }

    @Test
    fun `both failing reports the fallback's failure`() = runTest {
        val outcome = FallbackSearchSource(
            source(SearchOutcome.Failure("primary boom")),
            source(SearchOutcome.Failure("fallback boom")),
        ).search(query, 10)

        assertEquals("fallback boom", (outcome as SearchOutcome.Failure).detail)
    }
}
