package com.dewijones92.uniapp.ytdlp

import com.dewijones92.uniapp.common.HttpUrl

/** Result of asking the engine to extract [MediaMetadata] for a URL. */
public sealed interface ExtractionResult {

    public data class Success(val metadata: MediaMetadata) : ExtractionResult

    /** Expected, recoverable failures — modelled as values, not exceptions. */
    public sealed interface Failure : ExtractionResult {
        /** No extractor recognises this URL. */
        public data class UnsupportedUrl(val url: HttpUrl) : Failure

        /** The network was unreachable or the request timed out. */
        public data class Network(val detail: String) : Failure

        /** yt-dlp recognised the URL but extraction failed (geo-block, login wall, removal…). */
        public data class Extractor(val detail: String) : Failure
    }
}

/** What yt-dlp knows about a piece of media, without downloading it. */
public data class MediaMetadata(
    val id: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val formats: List<MediaFormat>,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(durationSeconds == null || durationSeconds > 0) { "durationSeconds must be positive when present" }
    }
}

/** One downloadable representation of the media. */
public data class MediaFormat(
    val formatId: String,
    val container: String,
    val width: Int?,
    val height: Int?,
    val isAudioOnly: Boolean,
    val fileSizeBytes: Long?,
) {
    init {
        require(formatId.isNotBlank()) { "formatId must not be blank" }
        require(!isAudioOnly || (width == null && height == null)) {
            "audio-only formats cannot have video dimensions"
        }
    }
}
