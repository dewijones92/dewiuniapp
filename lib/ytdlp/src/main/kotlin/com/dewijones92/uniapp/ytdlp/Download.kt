package com.dewijones92.uniapp.ytdlp

import com.dewijones92.uniapp.common.HttpUrl
import java.io.File

/** What to download and where to put it. */
public data class DownloadRequest(
    val url: HttpUrl,
    val targetDirectory: File,
    /** A [MediaFormat.formatId] from a prior [YtDlpEngine.extract]; null lets yt-dlp choose the best. */
    val formatId: String? = null,
)

/**
 * Events emitted while a download runs. [Completed] and [Failed] are
 * terminal; nothing is emitted after them.
 */
public sealed interface DownloadEvent {

    public data class Started(val url: HttpUrl) : DownloadEvent

    public data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val etaSeconds: Long?,
    ) : DownloadEvent {
        init {
            require(bytesDownloaded >= 0) { "bytesDownloaded must not be negative" }
            require(totalBytes == null || totalBytes >= bytesDownloaded) {
                "totalBytes must be at least bytesDownloaded when known"
            }
        }

        /** 0.0–1.0 when the total size is known, else null. */
        public val fraction: Double?
            get() = totalBytes?.takeIf { it > 0 }?.let { bytesDownloaded.toDouble() / it }
    }

    public data class Completed(val file: File) : DownloadEvent

    public data class Failed(val reason: ExtractionResult.Failure) : DownloadEvent
}
