package com.dewijones92.uniapp.queue

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
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

    private fun queue() = PlaybackQueue(controller, launcher, CoroutineScope(dispatcher))

    private fun podcast(id: String) = QueuedItem.Podcast(
        MediaItem(
            id = MediaItemId(id),
            sourceId = SourceId("feed"),
            title = id,
            publishedAt = null,
            duration = null,
            mediaUrl = HttpUrl.of("https://feeds.example.com/$id.mp3"),
        ),
    )

    @Test
    fun `enqueue adds to the end, playNext to the front`() {
        val q = queue()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))
        q.playNext(podcast("c"))

        assertEquals(listOf("c", "a", "b"), q.upNext.value.map { it.item.id.value })
    }

    @Test
    fun `removeAt drops that entry`() {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }
        q.removeAt(1)

        assertEquals(listOf("a", "c"), q.upNext.value.map { it.item.id.value })
    }

    @Test
    fun `move reorders an entry and ignores out-of-range indices`() {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(podcast(it)) }
        q.move(2, 0)
        assertEquals(listOf("c", "a", "b"), q.upNext.value.map { it.item.id.value })

        q.move(0, 9) // out of range → no change
        assertEquals(listOf("c", "a", "b"), q.upNext.value.map { it.item.id.value })
    }

    @Test
    fun `playNextInQueue plays and removes the head`() = runTest(dispatcher) {
        val q = queue()
        q.enqueue(podcast("a"))
        q.enqueue(podcast("b"))

        assertTrue(q.playNextInQueue())
        advanceUntilIdle()

        assertEquals("a", controller.state.value?.itemId?.value)
        assertEquals(listOf("b"), q.upNext.value.map { it.item.id.value })
    }

    @Test
    fun `playNextInQueue skips an unplayable item and plays the next`() = runTest(dispatcher) {
        val q = queue()
        // A podcast with neither a downloaded file nor a stream URL can't play.
        val unplayable = QueuedItem.Podcast(
            MediaItem(
                id = MediaItemId("bad"),
                sourceId = SourceId("feed"),
                title = "bad",
                publishedAt = null,
                duration = null,
                mediaUrl = null,
            ),
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
        assertEquals(listOf("c"), q.upNext.value.map { it.item.id.value })
    }
}
