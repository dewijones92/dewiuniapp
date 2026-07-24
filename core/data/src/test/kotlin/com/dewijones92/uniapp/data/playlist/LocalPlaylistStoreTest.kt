package com.dewijones92.uniapp.data.playlist

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.playlist.fake.InMemoryLocalPlaylistStore
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.PlaylistId
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaylistStoreTest {

    private val store = InMemoryLocalPlaylistStore()

    private fun video(id: String) = PlaylistItem(
        item = MediaItem(MediaItemId(id), SourceId("ch"), id, publishedAt = null, duration = null),
        playback = PlaylistPlayback.Video(HttpUrl.of("https://youtube.com/watch?v=$id")),
    )

    private fun podcast(id: String) = PlaylistItem(
        item = MediaItem(
            MediaItemId(id),
            SourceId("feed"),
            id,
            publishedAt = null,
            duration = null,
            mediaUrl = HttpUrl.of("https://feeds.example.com/$id.mp3"),
        ),
        playback = PlaylistPlayback.Podcast(),
    )

    private suspend fun items(id: PlaylistId) = store.observeItems(id).first()

    @Test
    fun `create then add mixes both pillars in order`() = runTest {
        val id = store.create("Mix")
        store.addItem(id, video("v1"))
        store.addItem(id, podcast("p1"))
        store.addItem(id, video("v2"))

        assertEquals(listOf("v1", "p1", "v2"), items(id).map { it.item.id.value })
        val playlist = store.observePlaylists().first().single()
        assertEquals("Mix", playlist.name)
        assertEquals(3, playlist.itemCount)
    }

    @Test
    fun `adding the same item id twice is a no-op`() = runTest {
        val id = store.create("P")
        store.addItem(id, video("v1"))
        store.addItem(id, video("v1"))
        assertEquals(1, items(id).size)
    }

    @Test
    fun `rename, remove item and delete`() = runTest {
        val id = store.create("Old")
        store.addItem(id, video("v1"))
        store.addItem(id, podcast("p1"))

        store.rename(id, "New")
        assertEquals("New", store.observePlaylists().first().single().name)

        store.removeItem(id, MediaItemId("v1"))
        assertEquals(listOf("p1"), items(id).map { it.item.id.value })

        store.delete(id)
        assertTrue(store.observePlaylists().first().isEmpty())
    }

    @Test
    fun `podcast item keeps its enclosure, video keeps its watch handle`() = runTest {
        val id = store.create("P")
        store.addItem(id, video("v1"))
        store.addItem(id, podcast("p1"))
        val byId = items(id).associateBy { it.item.id.value }

        assertTrue(byId.getValue("v1").playback is PlaylistPlayback.Video)
        assertTrue(byId.getValue("p1").playback is PlaylistPlayback.Podcast)
        assertEquals("https://feeds.example.com/p1.mp3", byId.getValue("p1").item.mediaUrl?.value)
    }
}
