package com.dewijones92.totum.video

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.innertube.history.YouTubeWatchHistory
import com.dewijones92.totum.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mirrors video watch-progress up to YouTube's servers (History + cross-device
 * resume, and the recommendations that follow from them) as playback advances —
 * the account-side counterpart to the app's local resume. Reports on a new
 * video, on finishing, and roughly every [REPORT_INTERVAL_MS]; a finished video
 * is reported once. The tracking URLs are registered separately by
 * [VideoPlaybackLauncher] via [YouTubeWatchHistory.beginSession].
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

                lastVideoId = videoId
                lastReportMs = now()
                if (finished) finishedVideoId = videoId
                // Fire-and-forget so the 500ms state stream is never blocked on the network.
                scope.launch {
                    val r = history.reportProgress(videoId, positionSec, lengthSec, finished)
                    Diag.log("yt-sync", "$videoId pos=$positionSec fin=$finished -> $r")
                }
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SEC = 1000f
        const val REPORT_INTERVAL_MS = 15_000L
        const val FINISH_THRESHOLD_SEC = 15f
    }
}
