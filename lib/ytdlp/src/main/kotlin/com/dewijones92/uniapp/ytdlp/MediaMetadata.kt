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
    /** The uploader's description/notes for this media, when the extractor provides one. */
    val description: String? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(durationSeconds == null || durationSeconds > 0) { "durationSeconds must be positive when present" }
    }
}

/** One downloadable/streamable representation of the media. */
public data class MediaFormat(
    val formatId: String,
    val container: String,
    val width: Int?,
    val height: Int?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val fileSizeBytes: Long?,
    /** Direct stream URL when the extractor provides one. */
    val url: String?,
) {
    init {
        require(formatId.isNotBlank()) { "formatId must not be blank" }
        require(hasVideo || hasAudio) { "a format must carry audio, video, or both" }
        require(hasVideo || (width == null && height == null)) {
            "audio-only formats cannot have video dimensions"
        }
    }

    public val isAudioOnly: Boolean
        get() = hasAudio && !hasVideo
}

/**
 * The format to hand to a player: pre-muxed audio+video at the highest
 * resolution, else the best audio-only stream. Null when nothing is
 * directly streamable.
 */
public fun MediaMetadata.bestPlayableFormat(): MediaFormat? {
    val streamable = formats.filter { it.url != null }
    return streamable.filter { it.hasVideo && it.hasAudio }.maxByOrNull { it.height ?: 0 }
        ?: streamable.filter { it.isAudioOnly }.maxByOrNull { it.fileSizeBytes ?: 0 }
}
