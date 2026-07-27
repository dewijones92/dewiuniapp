package com.dewijones92.totum.data.download

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.DownloadedMedia
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.asPlayable
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
        val strategy = DownloadStrategy { _, target, _ ->
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
        store.put(item.asPlayable(), DownloadState.Downloaded("/somewhere.media"))
        var called = false
        val strategy = DownloadStrategy { _, _, _ ->
            called = true
            flowOf()
        }

        manager(strategy, backgroundScope).download(item)

        assertFalse(called)
    }

    @Test
    fun `interrupted downloads are cleared on construction`() = runTest {
        store.put(item.asPlayable(), DownloadState.Downloading(500, 1000))

        // Unconfined so the manager's init cleanup runs eagerly at construction.
        manager(DownloadStrategy { _, _, _ -> flowOf() }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        assertEquals(DownloadState.NotDownloaded, store.get(item.id))
    }

    @Test
    fun `delete removes the file and record`() = runTest {
        val file = tempFolder.newFile("dl.media").apply { writeText("data") }
        store.put(item.asPlayable(), DownloadState.Downloaded(file.absolutePath))

        manager(DownloadStrategy { _, _, _ -> flowOf() }, backgroundScope).delete(item.id)

        assertFalse(file.exists())
        assertEquals(DownloadState.NotDownloaded, store.get(item.id))
    }

    @Test
    fun `an audio-only download does not satisfy a later request for the full media`() = runTest {
        val requested = mutableListOf<Boolean>()
        val manager = manager(
            DownloadStrategy { _, target, audioOnly ->
                requested.add(audioOnly)
                flowOf(DownloadState.Downloaded(target.path, audioOnly = audioOnly))
            },
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        manager.download(item, audioOnly = true)
        advanceUntilIdle()
        manager.download(item, audioOnly = false)
        advanceUntilIdle()

        // Both ran: the queue's audio grab must not make "Download" look done.
        assertEquals(listOf(true, false), requested)
        assertEquals(false, (manager.observe(item.id).first() as DownloadState.Downloaded).audioOnly)
    }

    @Test
    fun `a full download satisfies a later audio-only request`() = runTest {
        val requested = mutableListOf<Boolean>()
        val manager = manager(
            DownloadStrategy { _, target, audioOnly ->
                requested.add(audioOnly)
                flowOf(DownloadState.Downloaded(target.path, audioOnly = audioOnly))
            },
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        manager.download(item, audioOnly = false)
        advanceUntilIdle()
        manager.download(item, audioOnly = true)
        advanceUntilIdle()

        assertEquals(listOf(false), requested)
    }
}

private class InMemoryDownloadStore : DownloadStore {
    private val states = MutableStateFlow<Map<MediaItemId, PlayableAndState>>(emptyMap())

    override fun observeAll(): Flow<Map<MediaItemId, DownloadState>> =
        states.map { rows -> rows.mapValues { (_, row) -> row.state } }

    override fun observeDownloaded(): Flow<List<DownloadedMedia>> = states.map { rows ->
        rows.values.mapNotNull { row ->
            (row.state as? DownloadState.Downloaded)?.let {
                DownloadedMedia(row.item, it.localPath, it.audioOnly)
            }
        }
    }

    override suspend fun put(item: PlayableItem, state: DownloadState) =
        states.update { it + (item.item.id to PlayableAndState(item, state)) }

    override suspend fun get(id: MediaItemId): DownloadState =
        states.value[id]?.state ?: DownloadState.NotDownloaded

    override suspend fun remove(id: MediaItemId) { states.update { it - id } }
}

private data class PlayableAndState(val item: PlayableItem, val state: DownloadState)
