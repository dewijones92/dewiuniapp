package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFilterTest {

    private val states = mapOf(
        MediaItemId("done") to PlayState.Played,
        MediaItemId("half") to PlayState.InProgress(positionMs = 500, durationMs = 1_000),
        MediaItemId("fresh") to PlayState.Unplayed,
    )

    private val items = listOf(item("done"), item("half"), item("fresh"), item("unknown"))

    @Test
    fun `all keeps everything in order`() {
        assertEquals(
            listOf("done", "half", "fresh", "unknown"),
            items.filteredBy(MediaFilter.ALL, ::stateOf).map { it.id.value },
        )
    }

    /** The headline behaviour: finished items disappear, part-way ones stay. */
    @Test
    fun `unplayed hides finished but keeps part-way`() {
        assertEquals(
            listOf("half", "fresh", "unknown"),
            items.filteredBy(MediaFilter.UNPLAYED, ::stateOf).map { it.id.value },
        )
    }

    @Test
    fun `in-progress keeps only started-and-unfinished`() {
        assertEquals(
            listOf("half"),
            items.filteredBy(MediaFilter.IN_PROGRESS, ::stateOf).map { it.id.value },
        )
    }

    /** No recorded progress means unplayed, which is why a fresh install shows everything. */
    @Test
    fun `an item with no recorded progress counts as unplayed`() {
        assertTrue(MediaFilter.UNPLAYED.accepts(stateOf(MediaItemId("unknown"))))
        assertFalse(MediaFilter.IN_PROGRESS.accepts(stateOf(MediaItemId("unknown"))))
    }

    @Test
    fun `filtering an empty list is empty, not an error`() {
        assertEquals(emptyList<MediaItem>(), emptyList<MediaItem>().filteredBy(MediaFilter.UNPLAYED, ::stateOf))
    }

    private fun stateOf(id: MediaItemId) = states[id] ?: PlayState.Unplayed

    private fun item(id: String) = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId("s"),
        title = id,
        publishedAt = null,
        duration = null,
        mediaUrl = HttpUrl.of("https://example.com/$id.mp3"),
    )
}
