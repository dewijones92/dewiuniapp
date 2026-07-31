package com.dewijones92.totum.innertube.history.fake

import com.dewijones92.totum.innertube.history.WatchHistoryResult
import com.dewijones92.totum.innertube.history.YouTubeWatchHistory

/** In-memory [YouTubeWatchHistory] for tests and previews; records each call. */
public class FakeYouTubeWatchHistory(
    /** Mutable so a test can change the outcome mid-run, which is the interesting case. */
    public var result: WatchHistoryResult = WatchHistoryResult.Success,
) : YouTubeWatchHistory {

    public data class Report(
        val videoId: String,
        val positionSec: Float,
        val lengthSec: Float,
        val finished: Boolean,
    )

    public val sessions: MutableList<String> = mutableListOf()
    public val reports: MutableList<Report> = mutableListOf()

    override suspend fun beginSession(videoId: String) {
        sessions += videoId
    }

    override suspend fun reportProgress(
        videoId: String,
        positionSec: Float,
        lengthSec: Float,
        finished: Boolean,
    ): WatchHistoryResult {
        reports += Report(videoId, positionSec, lengthSec, finished)
        return result
    }
}
