package com.dewijones92.totum.video

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.innertube.history.WatchHistoryResult
import com.dewijones92.totum.innertube.history.YouTubeWatchHistory
import com.dewijones92.totum.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mirrors video watch-progress up to YouTube's servers (History + cross-device
 * resume, and the recommendations that follow from them) as playback advances —
 * the account-side counterpart to the app's local resume. Reports on a new
 * video, on finishing, and roughly every [REPORT_INTERVAL_MS]; a finished video
 * is reported once. [VideoPlaybackLauncher] opens the session via
 * [YouTubeWatchHistory.beginSession], which fetches the account-bearing tracking URLs.
 *
 * **Gated on the PILLAR, not on whether a video track is present.** It used to test
 * `hasVideo`, which excluded every YouTube video played in audio-only mode — "Listen",
 * and anything the queue had pre-downloaded as audio. With auto-download-audio on by
 * default that is most listening, so the bulk of what Dewi watched was invisible to his
 * own YouTube account and fed nothing back to the algorithm. Whether a picture is being
 * rendered has no bearing on whether YouTube should be told you watched something.
 */
class WatchHistorySync(
    private val playback: PlaybackController,
    private val history: YouTubeWatchHistory,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun start() {
        scope.launch {
            var lastVideoId: String? = null
            var lastReportMs = 0L
            var finishedVideoId: String? = null

            playback.state.collect { state ->
                // Podcasts are not YouTube's business; a YouTube video is, picture or not.
                if (state == null || state.kind != MediaKind.VIDEO) return@collect
                val lengthSec = (state.durationMs ?: 0L) / MILLIS_PER_SEC
                if (lengthSec <= 0f) return@collect

                val videoId = state.itemId.value
                val positionSec = state.positionMs / MILLIS_PER_SEC
                val finished = lengthSec - positionSec < FINISH_THRESHOLD_SEC
                if (finished && videoId == finishedVideoId) return@collect

                val due = videoId != lastVideoId || finished || now() - lastReportMs >= REPORT_INTERVAL_MS
                if (!due) return@collect

                val firstForVideo = videoId != lastVideoId
                lastVideoId = videoId
                lastReportMs = now()
                if (finished) finishedVideoId = videoId
                // Fire-and-forget so the 500ms state stream is never blocked on the network.
                scope.launch {
                    val result = history.reportProgress(videoId, positionSec, lengthSec, finished)
                    report(videoId, positionSec, finished, firstForVideo, result)
                }
            }
        }
    }

    private var lastResult: WatchHistoryResult? = null
    private var routineReports = 0

    /**
     * Says what changed, and counts what did not.
     *
     * This used to log every ping, and at one every fifteen seconds it was **31% of a whole
     * diagnostics report** — 125 of 400 entries, all of them saying "Success" again. The
     * buffer is bounded, so that is 125 entries of something else evicted: the report that
     * exposed all this covered 64 minutes with over half of it routine chatter.
     *
     * A run of identical Successes carries no information after the first. What does: the
     * first ping of a video (the session opened), any change of outcome, a finish, and
     * periodically that the run is still going, so a silent stop is still distinguishable
     * from everything being fine.
     */
    private fun report(
        videoId: String,
        positionSec: Float,
        finished: Boolean,
        firstForVideo: Boolean,
        result: WatchHistoryResult,
    ) {
        val changed = result != lastResult
        lastResult = result
        if (firstForVideo || finished || changed) {
            routineReports = 0
            Diag.log("yt-sync", "$videoId pos=$positionSec fin=$finished -> $result")
            return
        }
        routineReports++
        if (routineReports % LOG_EVERY == 0) {
            Diag.log("yt-sync", "$videoId pos=$positionSec -> $result (and $routineReports like it)")
        }
    }

    private companion object {
        const val MILLIS_PER_SEC = 1000f
        const val REPORT_INTERVAL_MS = 15_000L
        const val FINISH_THRESHOLD_SEC = 15f

        /** One line per two minutes of unchanged syncing, against one per fifteen seconds. */
        const val LOG_EVERY = 8
    }
}
