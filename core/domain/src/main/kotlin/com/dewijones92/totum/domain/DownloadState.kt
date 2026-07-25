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
     * downloads take, since they exist so you can *listen* offline. It is not shown
     * in the UI (a download is a download); it exists so that asking for the full
     * video afterwards isn't mistaken for "already downloaded".
     */
    public data class Downloaded(val localPath: String, val audioOnly: Boolean = false) : DownloadState {
        init {
            require(localPath.isNotBlank()) { "localPath must not be blank" }
        }
    }

    public data class Failed(val reason: String) : DownloadState
}

/**
 * The local file to play *as video*, or null.
 *
 * An audio-only download (what the queue fetches automatically) deliberately does
 * not qualify: playing it for a video request gives sound and a blank picture. Audio
 * playback uses [DownloadState.Downloaded.localPath] directly.
 */
public fun DownloadState.videoFileOrNull(): String? =
    (this as? DownloadState.Downloaded)?.takeIf { !it.audioOnly }?.localPath
