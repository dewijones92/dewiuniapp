package com.dewijones92.totum.video

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.innertube.browse.InnerTubeClient
import com.dewijones92.totum.innertube.browse.InnerTubeResponse
import com.dewijones92.totum.innertube.player.PlayerResponseParser
import com.dewijones92.totum.innertube.player.PlayerResult
import com.dewijones92.totum.innertube.player.StreamingData

/**
 * A second opinion on what a video can stream, asked of YouTube directly.
 *
 * yt-dlp has deprecated extraction without a JavaScript runtime, and Chaquopy has no way to
 * give it one — so on Android it silently loses formats. Measured 2026-07-30 on a
 * made-for-kids video: yt-dlp on this device offers ONE format at 360p, while the same
 * video asked of `/player` as the ANDROID client offers 32 with working URLs up to 1080p.
 * With `--js-runtimes node` on a laptop yt-dlp gets the full ladder too, which is what
 * proved the runtime — not YouTube, and not SABR — was the thing missing.
 *
 * Crucially the URLs this returns carry no `n` parameter, so nothing needs deciphering and
 * no JS runtime is implied. That is why this works on a phone where yt-dlp cannot.
 */
fun interface PlayerStreams {
    /**
     * Null when YouTube refuses or the call fails; the caller falls back to yt-dlp.
     *
     * The whole response, not just its streams: [PlayerResult.Success.details] and its
     * subtitles are what let this resolve a video on its own rather than only supplement an
     * extraction that has already been paid for.
     */
    suspend fun playerFor(videoId: String): PlayerResult.Success?
}

/** [PlayerStreams] over our own InnerTube client. */
class InnerTubePlayerStreams(private val innerTube: InnerTubeClient) : PlayerStreams {

    override suspend fun playerFor(videoId: String): PlayerResult.Success? {
        val response = runCatching { innerTube.player(videoId) }.getOrElse { failure ->
            Diag.warn("resolve", "second opinion for $videoId could not be fetched", failure)
            return null
        }
        val body = (response as? InnerTubeResponse.Success)?.body ?: run {
            Diag.log("resolve", "second opinion for $videoId: $response")
            return null
        }
        return when (val parsed = PlayerResponseParser.parse(body)) {
            is PlayerResult.Success -> parsed
            is PlayerResult.Unplayable -> {
                Diag.log("resolve", "second opinion for $videoId refused: ${parsed.reason}")
                null
            }
            is PlayerResult.Failure -> {
                Diag.warn("resolve", "second opinion for $videoId unreadable: ${parsed.detail}")
                null
            }
        }
    }
}

/**
 * The same quality ladder as [videoQualities], built from a `/player` response.
 *
 * Kept beside the yt-dlp mapping rather than merged with it: the two inputs describe formats
 * differently (a `MediaFormat` knows codecs and whether it has audio; a `PlayableFormat`
 * carries only a mime type), so a shared function would be a shared function with two
 * disjoint halves. What IS shared is [VideoQuality], which is the part that matters.
 */
internal fun StreamingData.videoQualities(): List<VideoQuality> {
    val audio = directlyPlayable
        .filter { it.mimeType?.startsWith("audio/") == true }
        .maxByOrNull { it.bitrate ?: 0 }
        ?.url

    return directlyPlayable
        .filter { it.height != null && it.mimeType?.startsWith("video/") == true }
        .groupBy { it.height!! }
        .mapNotNull { (height, atHeight) ->
            // A muxed stream needs no merge, so prefer it; otherwise pair video with the
            // best audio, exactly as the yt-dlp path does.
            val muxed = atHeight.firstOrNull { it.mimeType?.contains("mp4a") == true }
            val chosen = muxed ?: atHeight.maxByOrNull { it.bitrate ?: 0 } ?: return@mapNotNull null
            val url = chosen.url ?: return@mapNotNull null
            when {
                muxed != null -> VideoQuality("$height", "${height}p", height, url, audioUrl = null)
                audio != null -> VideoQuality("$height", "${height}p", height, url, audio)
                else -> null
            }
        }
        .sortedByDescending { it.height }
}

/** Best audio-only stream, for "Listen" mode and as the merge partner for a video-only one. */
internal fun StreamingData.bestAudioUrl(): HttpUrl? =
    directlyPlayable
        .filter { it.mimeType?.startsWith("audio/") == true }
        .maxByOrNull { it.bitrate ?: 0 }
        ?.url

/**
 * The best single stream that carries picture AND sound, or null when every format is split.
 *
 * The default the app plays, matching what the yt-dlp path picks: one stream is reliable and
 * data-friendly, and the quality menu offers the higher merged ladders on demand. Choosing the
 * tallest format here instead would quietly make every play a merged 2160p one.
 */
internal fun StreamingData.bestMuxedUrl(): HttpUrl? =
    directlyPlayable
        .filter { it.mimeType?.startsWith("video/") == true && it.mimeType?.contains("mp4a") == true }
        .maxByOrNull { it.height ?: 0 }
        ?.url
