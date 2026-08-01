package com.dewijones92.totum.ytdlp

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.SubtitleTrack

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
    /**
     * The uploader's own page, when the extractor provides one — for YouTube the
     * canonical `/channel/UC…` URL. Lets a media row navigate to its source
     * without the caller having to know the channel up front.
     */
    val uploaderUrl: String? = null,
    /** Chapters yt-dlp parsed from the description/metadata, earliest first; empty if none. */
    val chapters: List<ChapterInfo> = emptyList(),
    /**
     * Renderable subtitle tracks, author-provided first then auto-generated. Already
     * filtered to formats a player can decode and to a sane set of languages — see
     * the bridge's parsing, where YouTube's ~100 machine translations are dropped.
     */
    val subtitles: List<SubtitleTrack> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(durationSeconds == null || durationSeconds > 0) { "durationSeconds must be positive when present" }
    }
}

/** One chapter as yt-dlp reports it: a start offset in seconds and a title. */
public data class ChapterInfo(val startSeconds: Double, val title: String)

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
    /**
     * The extractor's codec strings (e.g. `vp09.00.50.08`, `av01.0.08M.08`,
     * `avc1.640028`, `mp4a.40.2`), or null when unknown. Needed because a device
     * that can't decode a codec must not be offered that stream: above 1080p
     * YouTube only publishes VP9/AV1, and picking blind means silent playback
     * failure.
     */
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    /**
     * BCP-47 language of this track (`en`, `en-US`, `hi`), or null when the extractor says
     * nothing. Only meaningful for audio: YouTube publishes dubbed tracks alongside the
     * original, and without this a track is just a bitrate.
     */
    val language: String? = null,
    /**
     * The extractor's preference for this track's language, higher being better; YouTube's
     * ORIGINAL track scores 10 and a dub scores less.
     *
     * Carried because bitrate alone picks the wrong one. Audio used to be chosen purely by file
     * size, so on any video whose dub is encoded larger than the original, the app played the
     * dub — a video in a language nobody asked for, with nothing in the logs to say why.
     */
    val languagePreference: Int? = null,
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
