package com.dewijones92.totum.data.content

import com.dewijones92.totum.common.Breadcrumbs
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.content.fake.InMemorySeenItemsTracker
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRefresherTest {

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

    private fun source(vararg updates: SourceUpdate) = SubscriptionItemsSource { updates.toList() }

    private suspend fun findNew(vararg updates: SourceUpdate) =
        ContentRefresher(listOf(source(*updates)), tracker).findNewContent()

    @Test
    fun `first run reports nothing - everything bootstraps as seen`() = runTest {
        val batch = findNew(SourceUpdate(feed("a"), listOf(item("a1", "a"), item("a2", "a"))))
        assertTrue(batch.newContent.isEmpty())
    }

    @Test
    fun `second run reports only items that appeared after the source was known`() = runTest {
        val a = feed("a")
        findNew(SourceUpdate(a, listOf(item("a1", "a")))) // bootstrap commits internally

        val new = findNew(SourceUpdate(a, listOf(item("a2", "a"), item("a1", "a")))).newContent

        assertEquals(1, new.size)
        assertEquals(a, new.single().source)
        assertEquals(listOf("a2"), new.single().items.map { it.id.value })
    }

    @Test
    fun `new items are re-reported until markDelivered is called`() = runTest {
        val a = feed("a")
        findNew(SourceUpdate(a, listOf(item("a1", "a")))) // bootstrap

        // Found, but NOT delivered — must appear again next run.
        val first = findNew(SourceUpdate(a, listOf(item("a2", "a"), item("a1", "a"))))
        assertEquals(listOf("a2"), first.newContent.single().items.map { it.id.value })
        val second = findNew(SourceUpdate(a, listOf(item("a2", "a"), item("a1", "a"))))
        assertEquals(listOf("a2"), second.newContent.single().items.map { it.id.value })

        // Now delivered — gone next run.
        second.markDelivered()
        val third = findNew(SourceUpdate(a, listOf(item("a2", "a"), item("a1", "a"))))
        assertTrue(third.newContent.isEmpty())
    }

    @Test
    fun `a failing source is skipped without hiding new content from the others`() = runTest {
        val a = feed("a")
        val b = feed("b")
        ContentRefresher(
            listOf(
                source(SourceUpdate(a, listOf(item("a1", "a")))),
                source(SourceUpdate(b, listOf(item("b1", "b")))),
            ),
            tracker,
        ).findNewContent().markDelivered()

        val failing = SubscriptionItemsSource { error("network down") }
        val healthy = source(SourceUpdate(b, listOf(item("b2", "b"), item("b1", "b"))))
        val new = ContentRefresher(listOf(failing, healthy), tracker).findNewContent().newContent

        assertEquals(listOf("b2"), new.single().items.map { it.id.value })
    }

    @Test
    fun `only sources with new items are returned`() = runTest {
        val a = feed("a")
        val b = feed("b")
        findNew(SourceUpdate(a, listOf(item("a1", "a"))), SourceUpdate(b, listOf(item("b1", "b"))))
            .markDelivered()

        val new = findNew(
            SourceUpdate(a, listOf(item("a1", "a"))), // unchanged
            SourceUpdate(b, listOf(item("b2", "b"), item("b1", "b"))), // b2 is new
        ).newContent

        assertEquals(1, new.size)
        assertEquals(b, new.single().source)
    }

    /**
     * A source that throws must say WHY, with its exception.
     *
     * This runs every six hours in the background where nobody is watching, and it used to
     * swallow whatever a source threw into an empty list. A pillar that had stopped working —
     * expired YouTube auth being the obvious one — simply never contributed new content again,
     * and "why was I not told about that episode?" had no answer anywhere. Skipping is right;
     * skipping quietly is not, so the reporting is pinned rather than left to good intentions.
     */
    @Test
    fun `a failing source is reported, with its cause`() = runTest {
        Breadcrumbs.clear()
        val broken = SubscriptionItemsSource { error("auth expired") }
        val working = source(SourceUpdate(feed("a"), listOf(item("1", "a"))))

        ContentRefresher(listOf(broken, working), tracker).findNewContent()

        val logged = Breadcrumbs.snapshot().filter { it.tag == "content" }
        assertTrue(
            "the failure must be named: $logged",
            logged.any { "could not be read" in it.message },
        )
        assertTrue(
            "and the run must summarise what it checked: $logged",
            logged.any { "1 failed" in it.message },
        )
    }

    /** A clean run still says it happened — an absent log must not mean "it never ran". */
    @Test
    fun `a run with no failures still reports what it checked`() = runTest {
        Breadcrumbs.clear()

        findNew(SourceUpdate(feed("a"), listOf(item("1", "a"))))

        assertTrue(
            "a healthy run must still leave a trace",
            Breadcrumbs.snapshot().any { it.tag == "content" && "0 failed" in it.message },
        )
    }
}
