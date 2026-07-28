package com.dewijones92.totum.video

import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.playback.PlaybackState
import com.dewijones92.totum.playback.fake.FakePlaybackController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What gets reported to YouTube.
 *
 * The behaviour that matters to Dewi: watching feeds his recommendations, so anything the app
 * plays from YouTube must be reported whether or not a picture is on screen. The sync used to
 * gate on `hasVideo`, which silently excluded every audio-only play — and with
 * auto-download-audio on by default that is most listening.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistorySyncTest {

    private val playback = FakePlaybackController()
    private val history = FakeYouTubeWatchHistory()

    /** A clock the test drives, so the report-interval logic is not wall-clock dependent. */
    private var clock = 0L

    private fun TestScope.sync() = WatchHistorySync(
        playback = playback,
        history = history,
        scope = backgroundScope,
        now = { clock },
    ).also { it.start() }

    /** The regression: audio-only playback of a YouTube video must still be reported. */
    @Test
    fun `an audio-only youtube video is reported`() = runTest {
        sync()
        runCurrent()

        playback.emitState(state("v1", kind = MediaKind.VIDEO, hasVideo = false))
        runCurrent()

        assertEquals(listOf("v1"), history.reports.map { it.videoId })
    }

    @Test
    fun `a video with a picture is reported`() = runTest {
        sync()
        runCurrent()

        playback.emitState(state("v1", kind = MediaKind.VIDEO, hasVideo = true))
        runCurrent()

        assertEquals(listOf("v1"), history.reports.map { it.videoId })
    }

    /** A podcast is not YouTube's business, picture or not. */
    @Test
    fun `a podcast is never reported`() = runTest {
        sync()
        runCurrent()

        playback.emitState(state("ep1", kind = MediaKind.PODCAST, hasVideo = false))
        runCurrent()

        assertTrue("podcasts must not be reported", history.reports.isEmpty())
    }

    @Test
    fun `reaching the end reports it as finished`() = runTest {
        sync()
        runCurrent()

        playback.emitState(state("v1", kind = MediaKind.VIDEO, hasVideo = false, positionMs = 0))
        runCurrent()
        playback.emitState(state("v1", kind = MediaKind.VIDEO, hasVideo = false, positionMs = 100_000))
        runCurrent()

        assertTrue("the finish must be reported", history.reports.any { it.finished })
    }

    /** An item with no known duration cannot be reported as a fraction watched. */
    @Test
    fun `nothing is reported without a duration`() = runTest {
        sync()
        runCurrent()

        playback.emitState(state("v1", kind = MediaKind.VIDEO, hasVideo = false, durationMs = null))
        runCurrent()

        assertTrue(history.reports.isEmpty())
    }

    private fun state(
        id: String,
        kind: MediaKind,
        hasVideo: Boolean,
        positionMs: Long = 0,
        durationMs: Long? = 100_000,
    ) = PlaybackState(
        itemId = MediaItemId(id),
        title = id,
        artist = null,
        artworkUrl = null,
        kind = kind,
        isPlaying = true,
        positionMs = positionMs,
        durationMs = durationMs,
        speed = 1f,
        hasVideo = hasVideo,
    )
}
