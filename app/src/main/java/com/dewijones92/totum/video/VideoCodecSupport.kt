package com.dewijones92.totum.video

import android.media.MediaCodecList
import com.dewijones92.totum.common.Diag
import android.media.MediaFormat as AndroidMediaFormat

/**
 * Whether this device can actually decode a given video stream.
 *
 * This exists because of a real failure: above 1080p, YouTube publishes only
 * video-only VP9/AV1 streams, and the quality ladder used to offer whichever
 * format happened to come first at each height. On a device without a decoder for
 * that codec (or without one that reaches those dimensions), selecting the quality
 * simply failed to play. Offering a quality the device cannot decode is the bug;
 * this is how the ladder finds out.
 */
public fun interface VideoCodecSupport {

    /** True if a decoder exists for [codec] at [width] x [height]. */
    public fun canDecode(codec: String?, width: Int?, height: Int?): Boolean

    public companion object {
        /** Assumes everything decodes — for tests and previews. */
        public val Permissive: VideoCodecSupport = VideoCodecSupport { _, _, _ -> true }
    }
}

/**
 * [VideoCodecSupport] backed by the platform's decoder list. Answers per codec and
 * per size, because plenty of devices decode a codec at 1080p but not at 2160p.
 *
 * Unknown codecs are allowed through: refusing what we can't identify would hide
 * playable streams, and playback failure is recoverable while a missing quality is
 * invisible.
 */
public class PlatformVideoCodecSupport : VideoCodecSupport {

    private val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS)

    override fun canDecode(codec: String?, width: Int?, height: Int?): Boolean {
        val mime = codec.toMimeType() ?: return true
        val format = AndroidMediaFormat().apply {
            setString(AndroidMediaFormat.KEY_MIME, mime)
            if (width != null && height != null) {
                setInteger(AndroidMediaFormat.KEY_WIDTH, width)
                setInteger(AndroidMediaFormat.KEY_HEIGHT, height)
            }
        }
        // findDecoderForFormat honours the size keys, so this answers "at this
        // resolution" rather than merely "this codec exists".
        val decodable = runCatching { decoders.findDecoderForFormat(format) != null }.getOrDefault(true)
        // Only rejections are logged: they are rare, and they are exactly what you
        // want to see when a quality you expected isn't on offer.
        if (!decodable) {
            Diag.log("codec", "no decoder for $codec at ${width}x$height — quality withheld")
        }
        return decodable
    }

    /** yt-dlp's codec string → the Android MIME type; null when unrecognised. */
    private fun String?.toMimeType(): String? {
        val codec = this?.lowercase() ?: return null
        return when {
            codec.startsWith("avc") || codec.startsWith("h264") -> AndroidMediaFormat.MIMETYPE_VIDEO_AVC
            codec.startsWith("hev") || codec.startsWith("h265") -> AndroidMediaFormat.MIMETYPE_VIDEO_HEVC
            codec.startsWith("vp9") || codec.startsWith("vp09") -> AndroidMediaFormat.MIMETYPE_VIDEO_VP9
            codec.startsWith("vp8") || codec.startsWith("vp08") -> AndroidMediaFormat.MIMETYPE_VIDEO_VP8
            codec.startsWith("av01") || codec.startsWith("av1") -> AndroidMediaFormat.MIMETYPE_VIDEO_AV1
            else -> null
        }
    }
}

/**
 * Rough preference between codecs when several are decodable at one height: prefer
 * the one most likely to be hardware-accelerated and cheap on battery. AVC is the
 * safest, then VP9, then HEVC, then AV1 (newest, most often software-decoded).
 */
internal fun String?.codecPreference(): Int {
    val codec = this?.lowercase() ?: return UNKNOWN
    return when {
        codec.startsWith("avc") || codec.startsWith("h264") -> AVC
        codec.startsWith("vp9") || codec.startsWith("vp09") -> VP9
        codec.startsWith("hev") || codec.startsWith("h265") -> HEVC
        codec.startsWith("av01") || codec.startsWith("av1") -> AV1
        else -> UNKNOWN
    }
}

private const val AVC = 0
private const val VP9 = 1
private const val HEVC = 2
private const val AV1 = 3
private const val UNKNOWN = Int.MAX_VALUE
