package com.dewijones92.totum.data.download

import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.asPlayable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Stopping a download that is running, and starting a failed one over.
 *
 * Dewi, 2026-08-07: *"e.g. cancel inprogress download"*. There was no way to: nothing held the
 * coroutine doing the fetching, so once started a download ran to completion whatever you did. On a
 * phone that is minutes and hundreds of megabytes for a video started by accident.
 *
 * Not holding the jobs had a second consequence nobody had noticed. [DownloadManager.delete] on a
 * download in flight removed the record while the coroutine carried on and wrote its next progress
 * update **straight back**, so the row reappeared seconds later and the bytes kept arriving — a
 * deletion that undid itself. One of the cases below is about exactly that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancelAndRetryTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val item = MediaItem(
        id = MediaItemId("ep-1"),
        sourceId = SourceId("feed"),
        title = "An episode",
        publishedAt = null,
        duration = null,
    ).asPlayable()

    /** A download that never finishes on its own, so a test can decide when it does. */
    private class HeldStrategy : DownloadStrategy {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<DownloadState>()
        var cancelled = false

        /** Where the manager told it to write — the partial file a cancel has to clean up. */
        var target: File? = null

        override fun download(item: PlayableItem, target: File, audioOnly: Boolean): Flow<DownloadState> = flow {
            // A real strategy has bytes on disk by now; the cancel has to find and remove them.
            this@HeldStrategy.target = target.also {
                it.parentFile?.mkdirs()
                it.writeText("half a podcast")
            }
            emit(DownloadState.Downloading(0, 1_000))
            started.complete(Unit)
            try {
                emit(release.await())
            } finally {
                // A cancelled coroutine unwinds through here, which is how the test knows the
                // fetching actually stopped rather than merely being forgotten about.
                if (!release.isCompleted) cancelled = true
            }
        }
    }

    private fun manager(strategy: DownloadStrategy, scope: TestScope, store: DownloadStore = InMemoryDownloadStore()) =
        DefaultDownloadManager(temp.root, store, strategy, scope) to store

    @Test
    fun `cancelling stops the coroutine actually doing the fetching`() = runTest {
        val strategy = HeldStrategy()
        val (manager, _) = manager(strategy, this)
        manager.download(item)
        strategy.started.await()

        manager.cancel(item.item.id)

        assertTrue("the strategy's flow must be cancelled, not just forgotten", strategy.cancelled)
    }

    @Test
    fun `cancelling forgets the download`() = runTest {
        val strategy = HeldStrategy()
        val (manager, store) = manager(strategy, this)
        manager.download(item)
        strategy.started.await()

        manager.cancel(item.item.id)
        advanceUntilIdle()

        assertEquals(DownloadState.NotDownloaded, store.get(item.item.id))
    }

    /**
     * And the half-written file goes with it.
     *
     * Leaving it would be invisible bytes: no record points at the file, so nothing in the app
     * could ever show it, play it or delete it — it would just be missing storage.
     */
    @Test
    fun `cancelling deletes the partial file`() = runTest {
        val strategy = HeldStrategy()
        val (manager, _) = manager(strategy, this)
        manager.download(item)
        strategy.started.await()
        val partial = requireNotNull(strategy.target) { "the strategy was never given a target" }
        assertTrue("setup: there must be a partial file to clean up", partial.exists())

        manager.cancel(item.item.id)
        advanceUntilIdle()

        assertFalse("a partial file with no record is storage nothing can reach", partial.exists())
    }

    /** Cancelling something that already finished is a no-op, not a crash. */
    @Test
    fun `cancelling a download that is not running does nothing`() = runTest {
        val strategy = HeldStrategy()
        val (manager, store) = manager(strategy, this)

        manager.cancel(MediaItemId("never-started"))
        advanceUntilIdle()

        assertEquals(DownloadState.NotDownloaded, store.get(MediaItemId("never-started")))
    }

    /**
     * THE LATENT BUG. Deleting a download in flight must also stop it.
     *
     * Without the job being held, the coroutine outlived the delete and wrote its next progress
     * update back, so the row reappeared and the fetch carried on.
     */
    @Test
    fun `deleting a download in flight stops it rather than letting it write itself back`() = runTest {
        val strategy = HeldStrategy()
        val (manager, store) = manager(strategy, this)
        manager.download(item)
        strategy.started.await()

        manager.delete(item.item.id)
        advanceUntilIdle()

        assertTrue("the fetch must be stopped", strategy.cancelled)
        assertEquals(
            "the record must stay gone rather than being written back by the running download",
            DownloadState.NotDownloaded,
            store.get(item.item.id),
        )
    }

    // ---- retry ---------------------------------------------------------------------------------

    @Test
    fun `retrying a failed download starts it again`() = runTest {
        val strategy = HeldStrategy()
        val (manager, store) = manager(strategy, this)
        manager.download(item)
        strategy.started.await()
        strategy.release.complete(DownloadState.Failed("no space"))
        advanceUntilIdle()
        assertTrue(store.get(item.item.id) is DownloadState.Failed)

        // A strategy that FINISHES, or the retry's coroutine would still be running when the test
        // ends and `runTest` fails on the leak rather than on anything it is asserting.
        val second = RecordingStrategy()
        val (retrying, _) = manager(second, this, store)
        retrying.retry(item.item.id)
        advanceUntilIdle()

        assertEquals("the retry must actually start a fetch", 1, second.audioOnlyAsked.size)
    }

    /**
     * And it fetches the SAME variant that was asked for.
     *
     * The queue downloads audio only; a retry that quietly fetched the whole video would spend
     * several times the data on a connection the person had already been careful about. The
     * requested flag has to be persisted for this, because a failed row is all a retry has.
     */
    @Test
    fun `retrying an audio-only download asks for audio only again`() = runTest {
        val strategy = HeldStrategy()
        val (manager, store) = manager(strategy, this)
        manager.download(item, audioOnly = true)
        strategy.started.await()
        strategy.release.complete(DownloadState.Failed("network went away"))
        advanceUntilIdle()

        val second = RecordingStrategy()
        val (retrying, _) = manager(second, this, store)
        retrying.retry(item.item.id)
        advanceUntilIdle()

        assertEquals("a retry must not silently upgrade to the full video", listOf(true), second.audioOnlyAsked)
    }

    @Test
    fun `retrying something with no record does nothing`() = runTest {
        val strategy = RecordingStrategy()
        val (manager, _) = manager(strategy, this)

        manager.retry(MediaItemId("unknown"))
        advanceUntilIdle()

        assertEquals(emptyList<Boolean>(), strategy.audioOnlyAsked)
    }

    private class RecordingStrategy : DownloadStrategy {
        val audioOnlyAsked = mutableListOf<Boolean>()
        override fun download(item: PlayableItem, target: File, audioOnly: Boolean): Flow<DownloadState> = flow {
            audioOnlyAsked += audioOnly
            emit(DownloadState.Downloaded(target.absolutePath, audioOnly))
        }
    }
}
