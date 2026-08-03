package com.dewijones92.totum.queue

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Offline, an item that can only come over the wire is declined at once rather than attempted.
 *
 * Attempting it is not harmless. The stall machinery would run its whole course — a 20-second
 * stall, two rescues, a give-up — about a minute of spinner to reach a conclusion
 * `ConnectivityManager` could have given instantly. On a plane, with a queue of eighty, that is the
 * difference between the queue working and the queue appearing broken.
 *
 * The downloaded copy must still play, which is the entire point of downloading it, and is the
 * assertion most at risk of being lost to an over-broad "offline means no playback" rule.
 *
 * Driven through [PlaybackQueue.peek] — the public one-off play — because it routes exactly as a
 * queued play does and reports whether the item was playable, which is the decision under test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSkipsUnavailableTest {

    private val dispatcher = StandardTestDispatcher()
    private val controller = FakePlaybackController()

    private fun queue(offline: Boolean) = PlaybackQueue(
        controller = controller,
        launcher = VideoPlaybackLauncher(
            VideoResolver(FakeYtDlpEngine(), SkipSegmentSource { emptyList() }),
            controller,
            FakeYouTubeWatchHistory(),
            InMemoryPlayHistoryStore(),
        ),
        scope = CoroutineScope(dispatcher),
        offline = { offline },
    )

    private fun streamOnly(id: String) = PlayableItem(
        item = MediaItem(
            id = MediaItemId(id),
            sourceId = SourceId("test"),
            title = id,
            publishedAt = null,
            duration = null,
            mediaUrl = HttpUrl.of("https://example.test/$id.mp3"),
        ),
        handle = PlayHandle.Podcast(),
    )

    private fun downloaded(id: String) = PlayableItem(
        item = MediaItem(
            id = MediaItemId(id),
            sourceId = SourceId("test"),
            title = id,
            publishedAt = null,
            duration = null,
            mediaUrl = HttpUrl.of("https://example.test/$id.mp3"),
        ),
        handle = PlayHandle.Podcast(localPath = "/data/$id.mp3"),
    )

    private fun video(id: String) = PlayableItem(
        item = MediaItem(
            id = MediaItemId(id),
            sourceId = SourceId("test"),
            title = id,
            publishedAt = null,
            duration = null,
            mediaUrl = null,
        ),
        handle = PlayHandle.Video(HttpUrl.of("https://youtube.test/watch?v=aaaaaaaaaaa")),
    )

    @Test
    fun `offline, a stream-only item is declined rather than attempted`() = runTest(dispatcher) {
        val queue = queue(offline = true)

        val played = queue.peek(streamOnly("episode"))
        advanceUntilIdle()

        assertEquals(false, played)
        assertNull("nothing should have been handed to the player", controller.lastItem)
    }

    /** The whole reason for downloading it. An over-broad offline rule would break exactly this. */
    @Test
    fun `offline, a downloaded item still plays`() = runTest(dispatcher) {
        val queue = queue(offline = true)

        val played = queue.peek(downloaded("episode"))
        advanceUntilIdle()

        assertEquals(true, played)
        assertEquals("/data/episode.mp3", controller.lastLocalPath)
    }

    /** A video is resolved over the network before a byte plays, so there is nothing to try. */
    @Test
    fun `offline, a video is declined without resolving`() = runTest(dispatcher) {
        val queue = queue(offline = true)

        val played = queue.peek(video("aaaaaaaaaaa"))
        advanceUntilIdle()

        assertEquals(false, played)
        assertNull(controller.lastItem)
    }

    @Test
    fun `online, a stream-only item plays as it always did`() = runTest(dispatcher) {
        val queue = queue(offline = false)

        val played = queue.peek(streamOnly("episode"))
        advanceUntilIdle()

        assertEquals(true, played)
        assertEquals(MediaItemId("episode"), controller.lastItem?.id)
    }

    /**
     * And the queue keeps going: offline, an advance past an unplayable item must reach the
     * downloaded one behind it rather than stopping at the first thing it cannot play.
     */
    @Test
    fun `offline, the queue advances past what it cannot play to what it can`() = runTest(dispatcher) {
        val queue = queue(offline = true)
        queue.enqueue(streamOnly("not-downloaded"))
        queue.enqueue(downloaded("on-the-device"))
        advanceUntilIdle()

        queue.playNextInQueue()
        advanceUntilIdle()

        assertEquals(MediaItemId("on-the-device"), controller.lastItem?.id)
    }
}
