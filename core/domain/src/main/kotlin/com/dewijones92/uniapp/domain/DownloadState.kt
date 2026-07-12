package com.dewijones92.uniapp.domain

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

    /** Complete and playable offline from [localPath]. */
    public data class Downloaded(val localPath: String) : DownloadState {
        init {
            require(localPath.isNotBlank()) { "localPath must not be blank" }
        }
    }

    public data class Failed(val reason: String) : DownloadState
}
