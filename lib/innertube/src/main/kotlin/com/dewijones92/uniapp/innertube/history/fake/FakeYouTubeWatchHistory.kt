package com.dewijones92.uniapp.innertube.history.fake

import com.dewijones92.uniapp.innertube.history.WatchHistoryResult
import com.dewijones92.uniapp.innertube.history.YouTubeWatchHistory

/** In-memory [YouTubeWatchHistory] for tests and previews; records each call. */
public class FakeYouTubeWatchHistory(
    private val result: WatchHistoryResult = WatchHistoryResult.Success,
) : YouTubeWatchHistory {

    public data class Report(
        val videoId: String,
        val positionSec: Float,
        val lengthSec: Float,
        val finished: Boolean,
    )

    public val sessions: MutableList<String> = mutableListOf()
    public val reports: MutableList<Report> = mutableListOf()

    override fun beginSession(videoId: String, playbackUrl: String?, watchtimeUrl: String?) {
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
