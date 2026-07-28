package com.dewijones92.totum.queue

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.queue.QueueEntry
import com.dewijones92.totum.data.queue.QueueGroup
import com.dewijones92.totum.data.queue.QueueSnapshot
import com.dewijones92.totum.data.queue.QueueStore
import com.dewijones92.totum.data.queue.fake.InMemoryQueueStore
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.playback.fake.FakePlaybackController
import com.dewijones92.totum.video.VideoPlaybackLauncher
import com.dewijones92.totum.video.VideoResolver
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
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

    /**
     * Dewi's report: "when I click play next on something already in the queue, it dups it."
     * Every add-path must move rather than duplicate — playNow already did, and the others
     * disagreed with it.
     */
    @Test
    fun `play next moves an already-queued item instead of duplicating it`() = runTest(dispatcher) {
        val q = queue()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))
        q.enqueue(podcast("c"))

        q.playNext(podcast("c"))
        advanceUntilIdle()

        assertEquals(listOf("c", "a", "b"), q.state.value.entries.map { it.item.item.id.value })
    }

    @Test
    fun `add to queue moves an already-queued item to the end`() = runTest(dispatcher) {
        val q = queue()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))

        q.enqueue(podcast("a"))
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), q.state.value.entries.map { it.item.item.id.value })
    }

    /**
     * The playing entry is exempt: removing it would drop the cursor and the queue would forget
     * where it was, so "play next" on what is already playing does nothing.
     */
    @Test
    fun `play next on the playing item leaves the queue and cursor alone`() = runTest(dispatcher) {
        val q = queue()
        q.playNow(podcast("a"))
        q.enqueue(podcast("b"))
        advanceUntilIdle()
        val cursorBefore = q.state.value.currentIndex

        q.playNext(podcast("a"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), q.state.value.entries.map { it.item.item.id.value })
        assertEquals(cursorBefore, q.state.value.currentIndex)
    }

    @Test
    fun `play all does not duplicate items already queued`() = runTest(dispatcher) {
        val q = queue()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))
        advanceUntilIdle()

        q.playAll(listOf(podcast("b"), podcast("c")))
        advanceUntilIdle()

        // b moved into the run rather than being duplicated; c is new; a is left where it was.
        val ids = q.state.value.entries.map { it.item.item.id.value }
        assertEquals(listOf("b", "c", "a"), ids)
        assertEquals("no duplicates", ids.size, ids.distinct().size)
    }

    /** A caller can hand over a list with repeats; re-opening the shorts reel does exactly that. */
    @Test
    fun `play all drops repeats within its own run`() = runTest(dispatcher) {
        val q = queue()

        q.playAll(listOf(podcast("a"), podcast("b"), podcast("a")))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), q.state.value.entries.map { it.item.item.id.value })
    }

    /** A queue already polluted by the old behaviour repairs itself rather than staying broken. */
    @Test
    fun `a saved queue containing duplicates is repaired on load`() = runTest(dispatcher) {
        val polluted = InMemoryQueueStore()
        polluted.save(
            QueueSnapshot(
                entries = listOf(podcast("a"), podcast("b"), podcast("a"), podcast("b")).map { QueueEntry(it) },
                currentIndex = 1,
            ),
        )

        val q = queue(polluted)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), q.state.value.entries.map { it.item.item.id.value })
        // The cursor still points at the entry it pointed at, not at whatever landed on index 1.
        assertEquals("b", q.state.value.current?.item?.item?.id?.value)
    }

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

        assertEquals(listOf("c", "a", "b"), q.state.value.upNext.map { it.item.item.id.value })
    }

    @Test
    fun `removeAt drops that entry`() {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }
        q.removeAt(1)

        assertEquals(listOf("a", "c"), q.state.value.upNext.map { it.item.item.id.value })
    }

    @Test
    fun `move reorders an entry and ignores out-of-range indices`() {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }
        q.move(2, 0)
        assertEquals(listOf("c", "a", "b"), q.state.value.upNext.map { it.item.item.id.value })

        q.move(0, 9) // out of range → no change
        assertEquals(listOf("c", "a", "b"), q.state.value.upNext.map { it.item.item.id.value })
    }

    @Test
    fun `advancing moves the cursor without consuming entries`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))

        assertTrue(q.playNextInQueue())
        advanceUntilIdle()

        assertEquals("a", controller.state.value?.itemId?.value)
        // Both entries remain; only the cursor moved, so you can go back to "a".
        assertEquals(listOf("a", "b"), q.state.value.entries.map { it.item.item.id.value })
        assertEquals(0, q.state.value.currentIndex)
        assertEquals(listOf("b"), q.state.value.upNext.map { it.item.item.id.value })
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
        assertTrue(q.state.value.upNext.isEmpty())
    }

    @Test
    fun `playNextInQueue on an empty queue returns false and plays nothing`() = runTest(dispatcher) {
        val q = queue()
        assertFalse(q.playNextInQueue())
        advanceUntilIdle()
        assertEquals(null, controller.state.value)
    }

    @Test
    fun `jumping plays that entry and keeps everything before it`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }

        q.jumpTo(1)
        advanceUntilIdle()

        assertEquals("b", controller.state.value?.itemId?.value)
        // "a" survives: jumping is navigation, not consumption.
        assertEquals(listOf("a", "b", "c"), q.state.value.entries.map { it.item.item.id.value })
        assertEquals(listOf("c"), q.state.value.upNext.map { it.item.item.id.value })
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
        assertEquals(listOf("a", "b"), restored.state.value.upNext.map { it.item.item.id.value })
    }

    @Test
    fun `hydration does not wipe a saved queue`() = runTest(dispatcher) {
        val saved = InMemoryQueueStore(QueueSnapshot(listOf(QueueEntry(podcast("kept")))))

        val q = queue(saved)
        advanceUntilIdle()

        assertEquals(listOf("kept"), q.state.value.upNext.map { it.item.item.id.value })
        assertEquals(listOf("kept"), saved.load().entries.map { it.item.item.id.value })
    }

    @Test
    fun `playAll tags its entries with the group so they can be dropped together`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        val group = QueueGroup("pl-1", "Mix")

        q.playAll(listOf(podcast("a"), podcast("b"), podcast("c")), group)
        advanceUntilIdle()

        // The first plays now; the rest are queued, all tagged.
        assertEquals(listOf("b", "c"), q.state.value.upNext.map { it.item.item.id.value })
        assertTrue(q.state.value.upNext.all { it.group == group })

        q.removeGroup("pl-1")
        assertTrue(q.state.value.upNext.isEmpty())
    }

    @Test
    fun `removeGroup leaves ungrouped entries and other groups alone`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("loose"))
        q.enqueue(podcast("a"), QueueGroup("g1", "One"))
        q.enqueue(podcast("b"), QueueGroup("g2", "Two"))

        q.removeGroup("g1")

        assertEquals(listOf("loose", "b"), q.state.value.upNext.map { it.item.item.id.value })
    }

    @Test
    fun `queueing during startup wins over the restored queue`() = runTest(dispatcher) {
        // Loading is suspending, so the user can act before it lands. Their action
        // must not be silently replaced by the saved queue.
        val saved = InMemoryQueueStore(QueueSnapshot(listOf(QueueEntry(podcast("old")))))
        val q = queue(saved)

        q.enqueue(podcast("just-added")) // before advanceUntilIdle, i.e. pre-hydration
        advanceUntilIdle()

        assertEquals(listOf("just-added"), q.state.value.upNext.map { it.item.item.id.value })
    }

    @Test
    fun `playNow joins the queue at the current position and keeps the rest`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("lined-up"))

        q.playNow(podcast("tapped"))
        advanceUntilIdle()

        assertEquals("tapped", controller.state.value?.itemId?.value)
        // The tapped item is a queue member now, and what was lined up follows it.
        assertEquals("tapped", q.state.value.current?.item?.item?.id?.value)
        assertEquals(listOf("lined-up"), q.state.value.upNext.map { it.item.item.id.value })
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
        assertEquals(listOf("a"), q.state.value.upNext.map { it.item.item.id.value })
    }

    @Test
    fun `peek plays without joining the queue`() = runTest(dispatcher) {
        val q = queue()
        advanceUntilIdle()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))

        q.peek(podcast("one-off"))
        advanceUntilIdle()

        assertEquals("one-off", controller.state.value?.itemId?.value)
        // Untouched queue, and the peeked item is not a member of it.
        assertEquals(listOf("a", "b"), q.state.value.entries.map { it.item.item.id.value })
        assertEquals(QueueSnapshot.NOTHING_PLAYING, q.state.value.currentIndex)
        assertEquals(listOf("a", "b"), q.state.value.upNext.map { it.item.item.id.value })
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
        assertEquals(listOf("y", "mine"), q.state.value.upNext.map { it.item.item.id.value })
    }
}
