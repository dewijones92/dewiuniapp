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
    /** The stream's video codec, for diagnostics and codec-aware selection. */
    val codec: String? = null,
)

/**
 * The selectable qualities for [this] video, highest first. A muxed format at a
 * given height wins (one stream, most reliable); otherwise a video-only stream
 * is paired with the best audio-only track for merging. Heights with no usable
 * stream are dropped.
 */
/** The best audio-only stream URL, for merging or for "Listen" (audio-only) playback. */
public fun MediaMetadata.bestAudioUrl(): HttpUrl? = formats
    .filter { it.isAudioOnly && it.url != null }
    .maxByOrNull { it.fileSizeBytes ?: 0 }
    ?.url?.let(HttpUrl::parse)

/**
 * The selectable qualities, **filtered to what this device can actually decode**.
 *
 * Above 1080p YouTube publishes only video-only VP9/AV1, so every high quality goes
 * down the merge path with an arbitrary codec. Offering one the device can't decode
 * meant selecting it just stopped playback, which is why [support] is consulted here
 * rather than left to fail at the decoder. Where several codecs are decodable at a
 * height, the most likely to be hardware-accelerated wins.
 */
public fun MediaMetadata.videoQualities(
    support: VideoCodecSupport = VideoCodecSupport.Permissive,
): List<VideoQuality> {
    val bestAudio = bestAudioUrl()

    return formats
        .filter { it.hasVideo && it.height != null && it.url != null }
        .filter { support.canDecode(it.videoCodec, it.width, it.height) }
        .groupBy { it.height!! }
        .mapNotNull { (height, atHeight) ->
            val decodable = atHeight.sortedBy { it.videoCodec.codecPreference() }
            val muxed = decodable.firstOrNull { it.hasAudio }
            when {
                muxed != null -> HttpUrl.parse(muxed.url!!)?.let { video ->
                    VideoQuality("$height", "${height}p", height, video, audioUrl = null, codec = muxed.videoCodec)
                }
                bestAudio != null -> decodable.first().let { best ->
                    HttpUrl.parse(best.url!!)?.let { video ->
                        VideoQuality("$height", "${height}p", height, video, bestAudio, best.videoCodec)
                    }
                }
                else -> null // video-only with no audio to merge — not playable on its own
            }
        }
        .sortedByDescending { it.height }
}
