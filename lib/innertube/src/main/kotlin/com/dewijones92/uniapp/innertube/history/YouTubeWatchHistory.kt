package com.dewijones92.uniapp.innertube.history

/**
 * Reports watch progress to YouTube's servers so a video's position syncs to
 * the account (History, and resume on other devices) — the same stats pings
 * SmartTube uses. The video pillar's counterpart to the app's local resume.
 */
public interface YouTubeWatchHistory {

    /**
     * Reports that [videoId] has been watched to [positionSec] of [lengthSec].
     * When [finished], marks it fully watched. No-op-ish when signed out.
     */
    public suspend fun reportProgress(
        videoId: String,
        positionSec: Float,
        lengthSec: Float,
        finished: Boolean,
    ): WatchHistoryResult
}

/** Outcome of a progress report; expected failures are values. */
public sealed interface WatchHistoryResult {
    public data object Success : WatchHistoryResult
    public data object SignedOut : WatchHistoryResult
    public data class Failure(val detail: String) : WatchHistoryResult
}
