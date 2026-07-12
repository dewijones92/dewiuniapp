package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultDownloadManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val item = MediaItem(
        id = MediaItemId("ep-1"),
        sourceId = SourceId("feed-1"),
        title = "Episode",
        publishedAt = null,
        duration = null,
        mediaUrl = HttpUrl.of("https://cdn.example.com/ep1.mp3"),
    )

    private val store = InMemoryDownloadStore()

    private fun manager(strategy: DownloadStrategy, scope: kotlinx.coroutines.CoroutineScope) =
        DefaultDownloadManager(tempFolder.root, store, strategy, scope)

    @Test
    fun `download records progress then completion`() = runTest {
        val strategy = DownloadStrategy { _, target ->
            flowOf(
                DownloadState.Downloading(500, 1000),
                DownloadState.Downloaded(target.absolutePath),
            )
        }

        manager(strategy, backgroundScope).download(item)

        val finalState = store.observeAll().map { it[item.id] }.first { it is DownloadState.Downloaded }
        assertTrue(finalState is DownloadState.Downloaded)
    }

    @Test
    fun `already-downloaded item is not re-downloaded`() = runTest {
        store.put(item.id, DownloadState.Downloaded("/somewhere.media"))
        var called = false
        val strategy = DownloadStrategy { _, _ ->
            called = true
            flowOf()
        }

        manager(strategy, backgroundScope).download(item)

        assertFalse(called)
    }

    @Test
    fun `interrupted downloads are cleared on construction`() = runTest {
        store.put(item.id, DownloadState.Downloading(500, 1000))

        // Unconfined so the manager's init cleanup runs eagerly at construction.
        manager(DownloadStrategy { _, _ -> flowOf() }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        assertEquals(DownloadState.NotDownloaded, store.get(item.id))
    }

    @Test
    fun `delete removes the file and record`() = runTest {
        val file = tempFolder.newFile("dl.media").apply { writeText("data") }
        store.put(item.id, DownloadState.Downloaded(file.absolutePath))

        manager(DownloadStrategy { _, _ -> flowOf() }, backgroundScope).delete(item.id)

        assertFalse(file.exists())
        assertEquals(DownloadState.NotDownloaded, store.get(item.id))
    }
}

private class InMemoryDownloadStore : DownloadStore {
    private val states = MutableStateFlow<Map<MediaItemId, DownloadState>>(emptyMap())
    override fun observeAll(): Flow<Map<MediaItemId, DownloadState>> = states
    override suspend fun put(id: MediaItemId, state: DownloadState) = states.update { it + (id to state) }
    override suspend fun get(id: MediaItemId): DownloadState = states.value[id] ?: DownloadState.NotDownloaded
    override suspend fun remove(id: MediaItemId) { states.update { it - id } }
}
