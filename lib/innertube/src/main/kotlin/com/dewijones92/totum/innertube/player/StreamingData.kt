package com.dewijones92.totum.innertube.player

import com.dewijones92.totum.common.HttpUrl

/**
 * What YouTube says it can stream for a video.
 *
 * The first stone of our own playback path. Today the app resolves streams through yt-dlp,
 * which extracts plain URLs — and YouTube is deliberately moving away from handing those
 * out. This reads the same player response yt-dlp does, but keeps the formats yt-dlp has to
 * discard: the ones with no [PlayableFormat.url], reachable only through
 * [serverAbrStreamingUrl] over YouTube's SABR/UMP protocol.
 *
 * Knowing about them is useful before we can fetch them. It is the difference between "this
 * video is 360p" and "this video is 1080p and we cannot reach it yet", which is what the app
 * currently gets wrong. See docs/todos/sabr-streaming.md.
 */
public data class StreamingData(
    val formats: List<PlayableFormat>,
    /**
     * Where SABR segments are requested from, when YouTube withholds direct URLs. Null when
     * every format is directly fetchable, which is still the common case.
     */
    val serverAbrStreamingUrl: HttpUrl? = null,
) {
    /** Formats we could play today — those with a direct URL. */
    public val directlyPlayable: List<PlayableFormat> get() = formats.filter { it.url != null }

    /** The best height YouTube offers, whether or not we can currently fetch it. */
    public val bestOfferedHeight: Int? get() = formats.mapNotNull { it.height }.maxOrNull()

    /** The best height we can actually fetch right now. */
    public val bestReachableHeight: Int? get() = directlyPlayable.mapNotNull { it.height }.maxOrNull()

    /**
     * True when YouTube is offering more than we can take — the SABR signature. Worth
     * naming: it is the one condition under which the app silently plays a worse video than
     * the one available.
     */
    public val degraded: Boolean
        get() {
            val offered = bestOfferedHeight ?: return false
            return offered > (bestReachableHeight ?: 0)
        }
}

/** One stream. [url] is null when YouTube will only serve it over SABR. */
public data class PlayableFormat(
    val itag: Int,
    val mimeType: String?,
    val height: Int?,
    val bitrate: Long?,
    val url: HttpUrl?,
)

public sealed interface PlayerResult {
    public data class Success(val streaming: StreamingData) : PlayerResult

    /** YouTube refused: age-gated, members-only, region-blocked, bot-checked. */
    public data class Unplayable(val reason: String) : PlayerResult
    public data class Failure(val detail: String) : PlayerResult
}
