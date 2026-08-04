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
import org.junit.Test

/**
 * A rescue asks the source to produce the stream again, not just the network to try again.
 *
 * The stall watchdog replays a dead stream from where it stopped. For a video that has always meant
 * something — the cached resolution is dropped, so a fresh URL is fetched. For everything else it
 * meant nothing at all: the same address was simply requested again.
 *
 * That gap was found by writing the stall tests on 2026-08-03 rather than by a report, and it made
 * the rescue much weaker than it appeared for torrents, which are the streams most likely to die
 * mid-item. Telling the home server to restart the remux behind an audio stream is the only second
 * thing there is to try.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayRefreshesTheSourceTest {

    private val dispatcher = StandardTestDispatcher()
    private val controller = FakePlaybackController()
    private val refreshed = mutableListOf<MediaItemId>()

    private fun queue(refreshThrows: Boolean = false) = PlaybackQueue(
        controller = controller,
        launcher = VideoPlaybackLauncher(
            VideoResolver(FakeYtDlpEngine(), SkipSegmentSource { emptyList() }),
            controller,
            FakeYouTubeWatchHistory(),
            InMemoryPlayHistoryStore(),
        ),
        scope = CoroutineScope(dispatcher),
        refresh = { item ->
            refreshed += item.item.id
            if (refreshThrows) error("the home server is unreachable")
        },
    )

    private fun torrent() = PlayableItem(
        item = MediaItem(
            id = MediaItemId("torrent:abc:7"),
            sourceId = SourceId("torrent"),
            title = "S01E01",
            publishedAt = null,
            duration = null,
            mediaUrl = HttpUrl.of("https://home.test/ts/stream/S01E01.mkv"),
        ),
        handle = PlayHandle.Podcast(audioUrl = HttpUrl.of("https://home.test/ts/audio/abc/7/index.m3u8")),
    )

    @Test
    fun `replaying asks the source to get the item ready again`() = runTest(dispatcher) {
        val queue = queue()
        queue.playAll(listOf(torrent()))
        advanceUntilIdle()

        queue.replayCurrent(positionMs = 30_000)
        advanceUntilIdle()

        assertEquals(listOf(MediaItemId("torrent:abc:7")), refreshed)
    }

    /** And it still plays, from where it stopped — the refresh is a step, not the outcome. */
    @Test
    fun `replaying still resumes from the given position`() = runTest(dispatcher) {
        val queue = queue()
        queue.playAll(listOf(torrent()))
        advanceUntilIdle()

        val replayed = queue.replayCurrent(positionMs = 30_000)
        advanceUntilIdle()

        assertEquals(true, replayed)
        assertEquals(30_000L, controller.lastStartPositionMs)
    }

    /**
     * A refresh that fails must not take the replay with it. The home server being unreachable is
     * exactly when a stall happens, and a rescue that throws in that case would turn a recoverable
     * stream into a stopped queue.
     */
    @Test
    fun `a refresh that fails still lets the replay happen`() = runTest(dispatcher) {
        val queue = queue(refreshThrows = true)
        queue.playAll(listOf(torrent()))
        advanceUntilIdle()

        val replayed = queue.replayCurrent(positionMs = 30_000)
        advanceUntilIdle()

        assertEquals("the replay must survive a failed refresh", true, replayed)
    }
}
