package com.dewijones92.uniapp.innertube.history

/**
 * Reports video watch-progress to YouTube's servers (History + cross-device
 * resume) using YouTube's own stats pings — the account-side counterpart to the
 * app's local resume. The tracking URLs come from the extractor's player
 * response (supplied via [beginSession]); this seam only handles the pings.
 */
public interface YouTubeWatchHistory {

    /**
     * Registers the stats tracking URLs for [videoId] (from the player
     * response) before any progress is reported. A missing [watchtimeUrl]
     * means the video simply won't sync. Called once per played video.
     */
    public fun beginSession(videoId: String, playbackUrl: String?, watchtimeUrl: String?)

    /**
     * Reports that [videoId] has been watched to [positionSec] of [lengthSec];
     * [finished] marks it fully watched. No-op when signed out or when no
     * session/tracking is known for the video.
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

    /** No tracking URLs registered for the video (e.g. non-YouTube, or capture failed). */
    public data object NoSession : WatchHistoryResult
    public data class Failure(val detail: String) : WatchHistoryResult
}
