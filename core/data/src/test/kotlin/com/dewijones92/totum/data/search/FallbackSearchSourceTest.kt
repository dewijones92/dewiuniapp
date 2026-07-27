package com.dewijones92.totum.data.search

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.Page
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

    private fun source(outcome: SearchOutcome) = SearchSource { _, _, _ -> outcome }

    private fun found(vararg titles: String) = SearchOutcome.Success(Page.last(titles.map(::hit)))

    private fun titles(outcome: SearchOutcome) =
        (outcome as SearchOutcome.Success).page.items.map { it.title }

    @Test
    fun `primary hits are used and the fallback is never consulted`() = runTest {
        var fallbackCalled = false
        val fallback = SearchSource { _, _, _ ->
            fallbackCalled = true
            found("from-fallback")
        }

        val outcome = FallbackSearchSource(source(found("from-primary")), fallback)
            .search(query, 10, after = null)

        assertEquals(listOf("from-primary"), titles(outcome))
        assertTrue("fallback must not run when the primary answered", !fallbackCalled)
    }

    @Test
    fun `a primary failure falls back`() = runTest {
        val outcome = FallbackSearchSource(
            source(SearchOutcome.Failure("boom")),
            source(found("from-fallback")),
        ).search(query, 10, after = null)

        assertEquals(listOf("from-fallback"), titles(outcome))
    }

    @Test
    fun `an empty primary result falls back too`() = runTest {
        val outcome = FallbackSearchSource(
            source(SearchOutcome.Success(Page.empty())),
            source(found("from-fallback")),
        ).search(query, 10, after = null)

        assertEquals(listOf("from-fallback"), titles(outcome))
    }

    @Test
    fun `both failing reports the fallback's failure`() = runTest {
        val outcome = FallbackSearchSource(
            source(SearchOutcome.Failure("primary boom")),
            source(SearchOutcome.Failure("fallback boom")),
        ).search(query, 10, after = null)

        assertEquals("fallback boom", (outcome as SearchOutcome.Failure).detail)
    }
}
