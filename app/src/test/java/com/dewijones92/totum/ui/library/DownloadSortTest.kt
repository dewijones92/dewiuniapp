package com.dewijones92.totum.ui.library

import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.ui.common.MediaSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * How the downloads list can be ordered.
 *
 * Dewi, 2026-08-07: *"sort by size, sort by other stuff etc etc etc"*. Size is the one that matters
 * on a full phone and the one [MediaSort] cannot express — it orders by what a [MediaItem] knows,
 * and an item does not know how many bytes its file takes.
 *
 * The design claim these hold is that [DownloadSort] **wraps** the shared sort rather than
 * restating it: every item-based order is the same comparator every other list uses, so they cannot
 * drift, and adding an option to `MediaSort` appears here without anything being kept in step.
 */
class DownloadSortTest {

    private data class Row(val item: MediaItem, val size: Long)

    private fun row(title: String, size: Long, days: Long? = null, seconds: Long? = null) = Row(
        MediaItem(
            id = MediaItemId(title),
            sourceId = SourceId("s"),
            title = title,
            publishedAt = days?.let { Instant.parse("2026-08-01T00:00:00Z").plusSeconds(it * DAY) },
            duration = seconds?.seconds,
        ),
        size,
    )

    private val rows = listOf(
        row("Banana", size = 300, days = 2, seconds = 60),
        row("apple", size = 100, days = 3, seconds = 30),
        row("Cherry", size = 200, days = 1, seconds = 90),
    )

    private fun DownloadSort.titles() = sortedBy(rows, item = { it.item }, size = { it.size }).map { it.item.title }

    @Test
    fun `largest first is what you open the list for when the phone is full`() {
        assertEquals(listOf("Banana", "Cherry", "apple"), DownloadSort.Largest.titles())
    }

    @Test
    fun `smallest first is the other way round`() {
        assertEquals(listOf("apple", "Cherry", "Banana"), DownloadSort.Smallest.titles())
    }

    @Test
    fun `an item order is the shared one, not a copy of it`() {
        assertEquals(
            "sorting by title must match MediaSort exactly, case-insensitively and all",
            MediaSort.TITLE.sortedBy(rows) { it.item }.map { it.item.title },
            DownloadSort.ByItem(MediaSort.TITLE).titles(),
        )
    }

    @Test
    fun `every item order behaves identically to the shared sort`() {
        MediaSort.entries.forEach { order ->
            assertEquals(
                "DownloadSort must delegate $order rather than reimplement it",
                order.sortedBy(rows) { it.item }.map { it.item.title },
                DownloadSort.ByItem(order).titles(),
            )
        }
    }

    /**
     * The menu is derived, not listed.
     *
     * If it were listed, adding an option to `MediaSort` would silently not appear on this screen —
     * the sort menu is exactly the kind of thing nobody re-checks.
     */
    @Test
    fun `the menu offers every shared order plus the two about the file`() {
        assertEquals("menu was ${DownloadSort.ALL}", MediaSort.entries.size + 2, DownloadSort.ALL.size)
        MediaSort.entries.forEach { order ->
            assertTrue("$order is missing from the menu", DownloadSort.ByItem(order) in DownloadSort.ALL)
        }
        assertTrue("largest missing from ${DownloadSort.ALL}", DownloadSort.Largest in DownloadSort.ALL)
        assertTrue("smallest missing from ${DownloadSort.ALL}", DownloadSort.Smallest in DownloadSort.ALL)
    }

    @Test
    fun `the default is the shared default, so downloads open like every other list`() {
        assertEquals(DownloadSort.ByItem(MediaSort.DEFAULT), DownloadSort.DEFAULT)
    }

    /** Equal sizes keep source order rather than shuffling, as the shared sorts do for equal keys. */
    @Test
    fun `equal sizes are left in the order they came in`() {
        val same = listOf(row("first", 100), row("second", 100), row("third", 100))
        assertEquals(
            listOf("first", "second", "third"),
            DownloadSort.Largest.sortedBy(same, item = { it.item }, size = { it.size }).map { it.item.title },
        )
    }

    private companion object {
        const val DAY = 86_400L
    }
}
