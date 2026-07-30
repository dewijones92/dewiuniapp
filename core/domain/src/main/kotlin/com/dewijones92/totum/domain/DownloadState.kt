package com.dewijones92.totum.domain

/**
 * How far a [MediaItem] has got towards being available offline. One concept
 * for both pillars — a podcast enclosure and a video stream download the same
 * way as far as the rest of the app is concerned.
 */
public sealed interface DownloadState {

    public data object NotDownloaded : DownloadState

    public data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : DownloadState {
        init {
            require(downloadedBytes >= 0) { "downloadedBytes must not be negative" }
            require(totalBytes == null || totalBytes >= downloadedBytes) {
                "totalBytes must be at least downloadedBytes when known"
            }
        }

        /** 0.0–1.0 when the total is known, else null (indeterminate). */
        public val fraction: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    /**
     * Complete and playable offline from [localPath].
     *
     * [audioOnly] marks a file fetched as audio only — what the queue's automatic
     * downloads take, since they exist so you can *listen* offline. It stops a later
     * request for the full video being mistaken for "already downloaded", and it is
     * **shown in the UI** as a distinct glyph (Dewi, 2026-07-25): with the queue fetching
     * audio automatically, most offline items are audio-only, so one glyph meaning both
     * "you can listen to this offline" and "you can watch this offline" is misleading.
     */
    public data class Downloaded(val localPath: String, val audioOnly: Boolean = false) : DownloadState {
        init {
            require(localPath.isNotBlank()) { "localPath must not be blank" }
        }
    }

    public data class Failed(val reason: String) : DownloadState
}

/**
 * Whether asking again could ever succeed.
 *
 * The automatic queue downloader retried every failure on every queue change, so two
 * members-only videos in a 59-item queue were re-attempted on every launch, for days —
 * visible in every diagnostics report sent on 2026-07-28, wasting data and burying the
 * event trail in failures that were never going to resolve.
 *
 * Matched on the extractor's own words, because that is all the failure carries. Deliberately
 * conservative: anything unrecognised is treated as transient and retried, since wrongly
 * giving up on a flaky connection is worse than one wasted request. A network blip, a 5xx or
 * a timeout says nothing about the content and must stay retryable.
 */
public val DownloadState.Failed.isPermanent: Boolean
    get() = reason.lowercase().let { text -> PERMANENT_MARKERS.any { it in text } }

/**
 * Phrases meaning "asking again cannot help", as the extractor words them.
 *
 * Mostly "this content is not available to you". Age-gating is here because it needs a
 * signed-in fetch the downloader cannot do, so retrying unattended will not fix it either.
 *
 * So is an ffmpeg downloader failure: our bundled ffmpeg has no network protocols by
 * design, so a format yt-dlp insists on fetching THROUGH ffmpeg — a live HLS stream — can
 * never succeed however many times it is asked. It read as transient and was re-attempted
 * on every queue change.
 */
private val PERMANENT_MARKERS = listOf(
    "join this channel",
    "members-only",
    "private video",
    "video unavailable",
    "removed by the uploader",
    "account associated with this video has been terminated",
    "sign in to confirm your age",
    "who has paid for access",
    "not made this video available",
    "ffmpeg exited with code",
)

/**
 * The local file to play *as video*, or null.
 *
 * An audio-only download (what the queue fetches automatically) deliberately does
 * not qualify: playing it for a video request gives sound and a blank picture. Audio
 * playback uses [DownloadState.Downloaded.localPath] directly.
 */
public fun DownloadState.videoFileOrNull(): String? =
    (this as? DownloadState.Downloaded)?.takeIf { !it.audioOnly }?.localPath
