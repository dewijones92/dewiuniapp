package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.MediaMetadata

/**
 * One selectable streaming quality for a video. [videoUrl] is either a muxed
 * stream (then [audioUrl] is null) or a video-only stream paired with a
 * separate [audioUrl] to be merged at playback — that's how qualities above
 * YouTube's muxed ceiling (~720p) stream.
 */
public data class VideoQuality(
    /** Stable id (the height), so a selection survives a re-resolve. */
    val id: String,
    val label: String,
    val height: Int,
    val videoUrl: HttpUrl,
    val audioUrl: HttpUrl?,
)

/**
 * The selectable qualities for [this] video, highest first. A muxed format at a
 * given height wins (one stream, most reliable); otherwise a video-only stream
 * is paired with the best audio-only track for merging. Heights with no usable
 * stream are dropped.
 */
public fun MediaMetadata.videoQualities(): List<VideoQuality> {
    val bestAudio = formats
        .filter { it.isAudioOnly && it.url != null }
        .maxByOrNull { it.fileSizeBytes ?: 0 }
        ?.url?.let(HttpUrl::parse)

    return formats
        .filter { it.hasVideo && it.height != null && it.url != null }
        .groupBy { it.height!! }
        .mapNotNull { (height, atHeight) ->
            val muxed = atHeight.firstOrNull { it.hasAudio }
            when {
                muxed != null -> HttpUrl.parse(muxed.url!!)?.let { video ->
                    VideoQuality("$height", "${height}p", height, video, audioUrl = null)
                }
                bestAudio != null -> HttpUrl.parse(atHeight.first().url!!)?.let { video ->
                    VideoQuality("$height", "${height}p", height, video, audioUrl = bestAudio)
                }
                else -> null // video-only with no audio to merge — not playable on its own
            }
        }
        .sortedByDescending { it.height }
}
