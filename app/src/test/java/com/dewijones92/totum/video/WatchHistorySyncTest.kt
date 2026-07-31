package com.dewijones92.totum.video

import com.dewijones92.totum.common.Breadcrumbs
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.innertube.history.WatchHistoryResult
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

    /**
     * The buffer is the scarce resource. Logging every ping made this **31% of a whole
     * diagnostics report** — 125 of 400 entries, every one of them "Success" again — which is
     * 125 entries of something else evicted. A run of identical outcomes says nothing after
     * the first, so it is counted rather than repeated.
     */
    @Test
    fun `an unchanging sync is counted, not repeated 30 times`() = runTest {
        Breadcrumbs.clear()
        sync()
        runCurrent()

        // Half an hour of a long video: 120 reports at one every fifteen seconds.
        repeat(120) { reportAt(it) }

        assertEquals("every report must still be SENT", 120, history.reports.size)
        val logged = syncLines()
        assertTrue("120 pings must not be 120 log lines, was $logged", logged < 20)
        assertTrue("but the run must not go silent either", logged > 1)
    }

    /** A change of outcome is the interesting case, and must never be swallowed by the counter. */
    @Test
    fun `a change of outcome is always logged`() = runTest {
        Breadcrumbs.clear()
        sync()
        runCurrent()

        repeat(40) { reportAt(it) }
        val quiet = syncLines()

        history.result = WatchHistoryResult.SignedOut
        reportAt(41)

        assertEquals("the signed-out turn must be said out loud", quiet + 1, syncLines())
    }

    /** One report interval of a long video, so the run is unchanging apart from the position. */
    private fun TestScope.reportAt(index: Int) {
        clock += 15_000
        playback.emitState(
            state(
                "v1",
                kind = MediaKind.VIDEO,
                hasVideo = false,
                positionMs = index * 15_000L,
                durationMs = 3_600_000,
            ),
        )
        runCurrent()
    }

    private fun syncLines() = Breadcrumbs.snapshot().count { it.tag == "yt-sync" }

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
