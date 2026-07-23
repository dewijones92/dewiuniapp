package com.dewijones92.uniapp.data.content

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.content.fake.InMemorySeenItemsTracker
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
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

    @Test
    fun `first run reports nothing - everything bootstraps as seen`() = runTest {
        val refresher = ContentRefresher(
            listOf(source(SourceUpdate(feed("a"), listOf(item("a1", "a"), item("a2", "a"))))),
            tracker,
        )

        assertTrue(refresher.findNewContent().isEmpty())
    }

    @Test
    fun `second run reports only items that appeared after the source was known`() = runTest {
        val a = feed("a")
        var items = listOf(item("a1", "a"))
        val refresher = ContentRefresher(listOf(source(SourceUpdate(a, items))), tracker)
        refresher.findNewContent() // bootstrap

        items = listOf(item("a2", "a"), item("a1", "a"))
        val refresher2 = ContentRefresher(listOf(source(SourceUpdate(a, items))), tracker)
        val new = refresher2.findNewContent()

        assertEquals(1, new.size)
        assertEquals(a, new.single().source)
        assertEquals(listOf("a2"), new.single().items.map { it.id.value })
    }

    @Test
    fun `a source is only surfaced once - re-running without changes reports nothing`() = runTest {
        val a = feed("a")
        ContentRefresher(listOf(source(SourceUpdate(a, listOf(item("a1", "a"))))), tracker).findNewContent()
        val withNew = ContentRefresher(
            listOf(source(SourceUpdate(a, listOf(item("a2", "a"), item("a1", "a"))))),
            tracker,
        )
        assertEquals(listOf("a2"), withNew.findNewContent().single().items.map { it.id.value })

        // Same feed again, nothing added -> nothing new.
        val again = ContentRefresher(
            listOf(source(SourceUpdate(a, listOf(item("a2", "a"), item("a1", "a"))))),
            tracker,
        )
        assertTrue(again.findNewContent().isEmpty())
    }

    @Test
    fun `a failing source is skipped without hiding new content from the others`() = runTest {
        val a = feed("a")
        val b = feed("b")
        // Bootstrap both.
        ContentRefresher(
            listOf(
                source(SourceUpdate(a, listOf(item("a1", "a")))),
                source(SourceUpdate(b, listOf(item("b1", "b")))),
            ),
            tracker,
        ).findNewContent()

        val failing = SubscriptionItemsSource { error("network down") }
        val healthy = source(SourceUpdate(b, listOf(item("b2", "b"), item("b1", "b"))))
        val new = ContentRefresher(listOf(failing, healthy), tracker).findNewContent()

        assertEquals(listOf("b2"), new.single().items.map { it.id.value })
    }

    @Test
    fun `only sources with new items are returned`() = runTest {
        val a = feed("a")
        val b = feed("b")
        ContentRefresher(
            listOf(source(SourceUpdate(a, listOf(item("a1", "a"))), SourceUpdate(b, listOf(item("b1", "b"))))),
            tracker,
        ).findNewContent()

        val new = ContentRefresher(
            listOf(
                source(
                    SourceUpdate(a, listOf(item("a1", "a"))), // unchanged
                    SourceUpdate(b, listOf(item("b2", "b"), item("b1", "b"))), // b2 is new
                ),
            ),
            tracker,
        ).findNewContent()

        assertEquals(1, new.size)
        assertEquals(b, new.single().source)
    }
}
