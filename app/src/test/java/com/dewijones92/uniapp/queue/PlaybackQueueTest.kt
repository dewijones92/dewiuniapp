package com.dewijones92.uniapp.queue

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.uniapp.data.queue.QueueEntry
import com.dewijones92.uniapp.data.queue.QueueGroup
import com.dewijones92.uniapp.data.queue.QueueStore
import com.dewijones92.uniapp.data.queue.fake.InMemoryQueueStore
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.PlayHandle
import com.dewijones92.uniapp.domain.PlayableItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import com.dewijones92.uniapp.video.VideoResolver
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueTest {

    private val dispatcher = StandardTestDispatcher()
    private val controller = FakePlaybackController()
    private val launcher = VideoPlaybackLauncher(
        VideoResolver(FakeYtDlpEngine(), SkipSegmentSource { emptyList() }),
        controller,
        FakeYouTubeWatchHistory(),
        InMemoryPlayHistoryStore(),
    )

    private val store = InMemoryQueueStore()

    private fun queue(withStore: QueueStore = store) =
        PlaybackQueue(controller, launcher, CoroutineScope(dispatcher), withStore)

    private fun podcast(id: String) = PlayableItem(
        MediaItem(
            id = MediaItemId(id),
            sourceId = SourceId("feed"),
            title = id,
            publishedAt = null,
            duration = null,
            mediaUrl = HttpUrl.of("https://feeds.example.com/$id.mp3"),
        ),
        PlayHandle.Podcast(),
    )

    @Test
    fun `enqueue adds to the end, playNext to the front`() {
        val q = queue()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))
        q.playNext(podcast("c"))

        assertEquals(listOf("c", "a", "b"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `removeAt drops that entry`() {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }
        q.removeAt(1)

        assertEquals(listOf("a", "c"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `move reorders an entry and ignores out-of-range indices`() {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }
        q.move(2, 0)
        assertEquals(listOf("c", "a", "b"), q.upNext.value.map { it.item.item.id.value })

        q.move(0, 9) // out of range → no change
        assertEquals(listOf("c", "a", "b"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `playNextInQueue plays and removes the head`() = runTest(dispatcher) {
        val q = queue()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))

        assertTrue(q.playNextInQueue())
        advanceUntilIdle()

        assertEquals("a", controller.state.value?.itemId?.value)
        assertEquals(listOf("b"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `playNextInQueue skips an unplayable item and plays the next`() = runTest(dispatcher) {
        val q = queue()
        // A podcast with neither a downloaded file nor a stream URL can't play.
        val unplayable = PlayableItem(
            MediaItem(
                id = MediaItemId("bad"),
                sourceId = SourceId("feed"),
                title = "bad",
                publishedAt = null,
                duration = null,
                mediaUrl = null,
            ),
            PlayHandle.Podcast(),
        )
        q.enqueue(unplayable)
        q.enqueue(podcast("good"))

        assertTrue(q.playNextInQueue())
        advanceUntilIdle()

        assertEquals("good", controller.state.value?.itemId?.value)
        assertTrue(q.upNext.value.isEmpty())
    }

    @Test
    fun `playNextInQueue on an empty queue returns false and plays nothing`() = runTest(dispatcher) {
        val q = queue()
        assertFalse(q.playNextInQueue())
        advanceUntilIdle()
        assertEquals(null, controller.state.value)
    }

    @Test
    fun `playFromQueue plays that entry and drops it and everything before it`() = runTest(dispatcher) {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }

        q.playFromQueue(1)
        advanceUntilIdle()

        assertEquals("b", controller.state.value?.itemId?.value)
        assertEquals(listOf("c"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `the queue is saved on change and hydrated back`() = runTest(dispatcher) {
        val first = queue()
        advanceUntilIdle() // let hydration of the (empty) store settle
        first.enqueue(podcast("a"))
        first.enqueue(podcast("b"))
        advanceUntilIdle()

        // A fresh queue over the same store comes back with the same entries.
        val restored = queue()
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), restored.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `hydration does not wipe a saved queue`() = runTest(dispatcher) {
        val saved = InMemoryQueueStore(listOf(QueueEntry(podcast("kept"))))

        val q = queue(saved)
        advanceUntilIdle()

        assertEquals(listOf("kept"), q.upNext.value.map { it.item.item.id.value })
        assertEquals(listOf("kept"), saved.load().map { it.item.item.id.value })
    }

    @Test
    fun `playAll tags its entries with the group so they can be dropped together`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        val group = QueueGroup("pl-1", "Mix")

        q.playAll(listOf(podcast("a"), podcast("b"), podcast("c")), group)
        advanceUntilIdle()

        // The first plays now; the rest are queued, all tagged.
        assertEquals(listOf("b", "c"), q.upNext.value.map { it.item.item.id.value })
        assertTrue(q.upNext.value.all { it.group == group })

        q.removeGroup("pl-1")
        assertTrue(q.upNext.value.isEmpty())
    }

    @Test
    fun `removeGroup leaves ungrouped entries and other groups alone`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("loose"))
        q.enqueue(podcast("a"), QueueGroup("g1", "One"))
        q.enqueue(podcast("b"), QueueGroup("g2", "Two"))

        q.removeGroup("g1")

        assertEquals(listOf("loose", "b"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `queueing during startup wins over the restored queue`() = runTest(dispatcher) {
        // Loading is suspending, so the user can act before it lands. Their action
        // must not be silently replaced by the saved queue.
        val saved = InMemoryQueueStore(listOf(QueueEntry(podcast("old"))))
        val q = queue(saved)

        q.enqueue(podcast("just-added")) // before advanceUntilIdle, i.e. pre-hydration
        advanceUntilIdle()

        assertEquals(listOf("just-added"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `playNow plays the item and keeps the rest of the queue`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("lined-up"))

        q.playNow(podcast("tapped"))
        advanceUntilIdle()

        assertEquals("tapped", controller.state.value?.itemId?.value)
        assertEquals(listOf("lined-up"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `playNow on an already-queued item moves it rather than duplicating`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))

        q.playNow(podcast("b"))
        advanceUntilIdle()

        assertEquals("b", controller.state.value?.itemId?.value)
        assertEquals(listOf("a"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `peek plays without touching the queue`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))

        q.peek(podcast("one-off"))
        advanceUntilIdle()

        assertEquals("one-off", controller.state.value?.itemId?.value)
        assertEquals(listOf("a", "b"), q.upNext.value.map { it.item.item.id.value })
    }

    @Test
    fun `playAll inserts its run ahead of the existing queue instead of replacing it`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("mine"))

        q.playAll(listOf(podcast("x"), podcast("y")), QueueGroup("pl", "Mix"))
        advanceUntilIdle()

        // x plays now; y is queued ahead of what was already there, and "mine" survives.
        assertEquals("x", controller.state.value?.itemId?.value)
        assertEquals(listOf("y", "mine"), q.upNext.value.map { it.item.item.id.value })
    }
}
