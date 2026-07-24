package com.dewijones92.uniapp.data.history

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.uniapp.data.playlist.PlaylistItem
import com.dewijones92.uniapp.data.playlist.PlaylistPlayback
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryStoreTest {

    private fun video(id: String) = PlaylistItem(
        item = MediaItem(MediaItemId(id), SourceId("ch"), id, publishedAt = null, duration = null),
        playback = PlaylistPlayback.Video(HttpUrl.of("https://youtube.com/watch?v=$id")),
    )

    private suspend fun ids(store: InMemoryPlayHistoryStore) =
        store.observe().first().map { it.item.id.value }

    @Test
    fun `most-recently-played comes first`() = runTest {
        val store = InMemoryPlayHistoryStore()
        store.record(video("a"))
        store.record(video("b"))
        assertEquals(listOf("b", "a"), ids(store))
    }

    @Test
    fun `replaying an item moves it to the front without duplicating`() = runTest {
        val store = InMemoryPlayHistoryStore()
        store.record(video("a"))
        store.record(video("b"))
        store.record(video("a"))
        assertEquals(listOf("a", "b"), ids(store))
    }

    @Test
    fun `history is capped at the limit, dropping the oldest`() = runTest {
        val store = InMemoryPlayHistoryStore(limit = 2)
        store.record(video("a"))
        store.record(video("b"))
        store.record(video("c"))
        assertEquals(listOf("c", "b"), ids(store))
    }

    @Test
    fun `clear empties the history`() = runTest {
        val store = InMemoryPlayHistoryStore()
        store.record(video("a"))
        store.clear()
        assertTrue(ids(store).isEmpty())
    }
}
