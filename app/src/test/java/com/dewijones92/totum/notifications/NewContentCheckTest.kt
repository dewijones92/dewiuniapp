package com.dewijones92.totum.notifications

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.content.ContentRefresher
import com.dewijones92.totum.data.content.SourceUpdate
import com.dewijones92.totum.data.content.SubscriptionItemsSource
import com.dewijones92.totum.data.content.fake.InMemorySeenItemsTracker
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one check both the six-hourly worker and the "check now" button run.
 *
 * Its four outcomes are the point. They used to be a boolean and a swallowed exception, which is
 * how "I never get notified" and "there was nothing new" became indistinguishable — and why a
 * background job could retry every six hours for weeks leaving nothing to diagnose it with.
 *
 * The seen-state assertions matter as much as the outcomes: marking items seen when they were
 * never shown loses them permanently, and that failure is invisible at the time it happens.
 */
class NewContentCheckTest {

    private val tracker = InMemorySeenItemsTracker()

    private fun feed(id: String) =
        MediaSource.PodcastFeed(SourceId(id), id, HttpUrl.of("https://example.com/$id.xml"))

    private fun item(id: String, source: String) = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId(source),
        title = id,
        publishedAt = null,
        duration = null,
    )

    private fun refresher(vararg updates: SourceUpdate) =
        ContentRefresher(listOf(SubscriptionItemsSource { updates.toList() }), tracker)

    private val oneNewItem = arrayOf(SourceUpdate(feed("a"), listOf(item("1", "a"))))

    /** First run bootstraps everything as seen, so there is genuinely nothing to tell anyone. */
    @Test
    fun `nothing new is reported as such`() = runTest {
        val check = NewContentCheck(refresher(*oneNewItem)) { true }

        assertEquals(NewContentCheck.Outcome.NothingNew, check.run())
    }

    @Test
    fun `new items that are delivered are counted`() = runTest {
        refresher(*oneNewItem).findNewContent().markDelivered()
        val withMore = refresher(
            SourceUpdate(feed("a"), listOf(item("1", "a"), item("2", "a"))),
        )

        val outcome = NewContentCheck(withMore) { true }.run()

        assertEquals(NewContentCheck.Outcome.Notified(items = 1, sources = 1), outcome)
    }

    /**
     * The case that must never quietly consume items: if the user was not shown them — almost
     * always because notification permission was never granted — they stay unseen so the next
     * run finds them again. Marking them seen here would lose them for good.
     */
    @Test
    fun `items that could not be delivered are kept unseen`() = runTest {
        refresher(*oneNewItem).findNewContent().markDelivered()
        val withMore = refresher(SourceUpdate(feed("a"), listOf(item("1", "a"), item("2", "a"))))

        val outcome = NewContentCheck(withMore) { false }.run()

        assertEquals(NewContentCheck.Outcome.Undelivered(items = 1), outcome)
        // Still findable, which is the whole reason delivery and detection are separate.
        val again = NewContentCheck(withMore) { true }.run()
        assertEquals(NewContentCheck.Outcome.Notified(items = 1, sources = 1), again)
    }

    /**
     * A throw is reported WITH its cause rather than swallowed. `NewContentWorker` used to end
     * `.getOrElse { Result.retry() }`, discarding the exception entirely.
     */
    @Test
    fun `a failure carries its cause instead of vanishing`() = runTest {
        refresher(*oneNewItem).findNewContent().markDelivered()
        val withMore = refresher(SourceUpdate(feed("a"), listOf(item("1", "a"), item("2", "a"))))

        // Notifying is the realistic place to throw — posting a notification touches the
        // platform, and this is exactly what the worker used to discard into a bare retry.
        val outcome = NewContentCheck(withMore) { error("notification manager unavailable") }.run()

        assertTrue("expected Failed, got $outcome", outcome is NewContentCheck.Outcome.Failed)
        assertEquals(
            "notification manager unavailable",
            (outcome as NewContentCheck.Outcome.Failed).error.message,
        )
    }
}
