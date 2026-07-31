package com.dewijones92.totum.innertube.history

/**
 * Reports video watch-progress to YouTube's servers (History + cross-device
 * resume) using YouTube's own stats pings — the account-side counterpart to the
 * app's local resume.
 *
 * This seam **owns where the pings go**, which it did not use to. The tracking URLs were
 * previously handed in by the caller from the extractor's player response; because the
 * extractor runs unauthenticated, those URLs belonged to an anonymous session and every
 * ping credited nobody while still returning HTTP 204. Fetching them here, authenticated,
 * is the fix — and it means a caller cannot supply the wrong ones by mistake.
 */
public interface YouTubeWatchHistory {

    /**
     * Prepares [videoId] for reporting, fetching its account-bearing tracking URLs. Called
     * once per played video, before any progress is reported; a video whose URLs cannot be
     * fetched simply won't sync.
     */
    public suspend fun beginSession(videoId: String)

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
