package com.dewijones92.totum.data.podcast

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.net.FetchResult
import com.dewijones92.totum.data.net.HttpTextFetcher
import com.dewijones92.totum.data.subscription.fake.InMemorySubscriptionStore
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * A podcast that stops updating has to say so.
 *
 * Refreshing used to swallow every failure in three bare `return`s — not a podcast feed, could
 * not fetch, would not parse — leaving the stored episodes intact, which is right, and telling
 * nobody, which is not. A feed that moved or began serving malformed XML looked exactly like one
 * with no new episodes, indefinitely, and "why has this not updated in three weeks?" could not be
 * answered from a diagnostics report at all.
 *
 * The video pillar learned this lesson several times over. These tests are it arriving on the
 * podcast side.
 */
class RefreshReportingTest {

    private val store = InMemorySubscriptionStore()

    private fun feed(id: String) = Subscription(
        source = MediaSource.PodcastFeed(
            id = SourceId(id),
            title = "Feed $id",
            feedUrl = HttpUrl.of("https://example.test/$id.xml"),
            websiteUrl = null,
        ),
        subscribedAt = Instant.EPOCH,
    )

    /**
     * The REAL parser, fed real XML. [RssParser] is a concrete class rather than a port, and
     * substituting one here would only prove that a fake returns what it was told to — the
     * question is whether a genuinely malformed feed is reported, so genuinely malformed XML is
     * what it gets.
     */
    private fun repository(fetch: (HttpUrl) -> FetchResult) = DefaultPodcastRepository(
        fetcher = HttpTextFetcher { url -> fetch(url) },
        store = store,
        clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
    )

    @Test
    fun `an unreachable feed is reported, not swallowed`() = runTest {
        store.saveSource(feed("a"), emptyList())
        val repo = repository { FetchResult.Failure("HTTP 404") }

        val report = repo.refresh()

        assertEquals(1, report.failures.size)
        val failure = report.failures.single()
        assertTrue("expected Unreachable, got $failure", failure is FeedRefreshFailure.Unreachable)
        assertEquals(SourceId("a"), failure.id)
        assertTrue("the reason must survive to the report", failure.describe().contains("404"))
    }

    @Test
    fun `a feed that will not parse is reported as such, since the fix differs`() = runTest {
        store.saveSource(feed("a"), emptyList())
        val repo = repository { FetchResult.Success(MALFORMED) }

        val report = repo.refresh()

        val failure = report.failures.single()
        assertTrue("expected Unparseable, got $failure", failure is FeedRefreshFailure.Unparseable)
        assertTrue(failure.describe().contains("would not parse"))
    }

    /**
     * One broken feed must not stop the others — the loop used to `return` out of a single
     * feed's helper, which was correct, but nothing proved the outer loop carried on.
     */
    @Test
    fun `a broken feed does not stop the rest from updating`() = runTest {
        store.saveSource(feed("broken"), emptyList())
        store.saveSource(feed("fine"), emptyList())
        val repo = repository { url ->
            if ("broken" in url.value) FetchResult.Failure("boom") else FetchResult.Success(VALID)
        }

        val report = repo.refresh()

        assertEquals(listOf(SourceId("fine")), report.updated)
        assertEquals(listOf(SourceId("broken")), report.failures.map { it.id })
        assertEquals(2, report.total)
    }

    /**
     * Everything failing at once is a different story from one feed failing, and worth
     * distinguishing: it nearly always means the network rather than the feeds.
     */
    @Test
    fun `every feed failing is recognisable as such`() = runTest {
        store.saveSource(feed("a"), emptyList())
        store.saveSource(feed("b"), emptyList())
        val repo = repository { FetchResult.Failure("offline") }

        val report = repo.refresh()

        assertTrue("two failures and no successes is allFailed", report.allFailed)
        assertEquals(2, report.failures.size)
    }

    @Test
    fun `a healthy refresh reports no failures`() = runTest {
        store.saveSource(feed("a"), emptyList())
        val repo = repository { FetchResult.Success(VALID) }

        val report = repo.refresh()

        assertEquals(emptyList<FeedRefreshFailure>(), report.failures)
        assertEquals(listOf(SourceId("a")), report.updated)
    }

    private companion object {
        val VALID = """<?xml version="1.0"?><rss version="2.0"><channel><title>A feed</title>
            </channel></rss>
        """.trimIndent()

        /** Unclosed tag: the real parser must reject this, not the test's opinion of it. */
        const val MALFORMED = "<rss><channel><title>broken"
    }
}
