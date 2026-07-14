package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.innertube.history.YouTubeWatchHistory
import com.dewijones92.uniapp.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mirrors video watch-progress up to YouTube's servers (History + cross-device
 * resume) as playback advances — the account-side counterpart to the app's
 * local resume. Watches the one playback state: any item with a video track is
 * a YouTube video here (podcasts are audio-only, so they're skipped). Reports
 * on a new video, on finishing, and roughly every [REPORT_INTERVAL_MS]; a
 * finished video is reported once. The tracking URLs are registered separately
 * by [VideoPlaybackLauncher] via [YouTubeWatchHistory.beginSession].
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
                if (state == null || !state.hasVideo) return@collect
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
                    android.util.Log.i("dewidebug", "yt-sync $videoId pos=$positionSec fin=$finished -> $r")
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
