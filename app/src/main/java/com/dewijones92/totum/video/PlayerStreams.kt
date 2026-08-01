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
class InnerTubePlayerStreams(
    private val innerTube: InnerTubeClient,
    /**
     * The signed-in account, asked only when the anonymous call is refused.
     *
     * Age-restricted videos are the case: report 0.1.289 had three failing with "Sign in to
     * confirm your age… rated 15". yt-dlp has no credentials, but this app holds a YouTube
     * account already, and YouTube serves a rated video to a signed-in adult.
     *
     * Null disables the fallback, which is what tests and a signed-out app want.
     */
    private val account: AccountPlayer? = null,
) : PlayerStreams {

    /** What the signed-in retry needs: a token, and the timestamp streams are signed against. */
    fun interface AccountPlayer {
        suspend fun playerFor(videoId: String): PlayerResult?
    }

    override suspend fun playerFor(videoId: String): PlayerResult.Success? {
        val anonymous = anonymousPlayer(videoId)
        (anonymous as? PlayerResult.Success)?.let { return it }
        // Only now, because the signed-in call costs a token refresh and a second round trip, and
        // the overwhelming majority of videos never need it.
        val signedIn = account?.playerFor(videoId) as? PlayerResult.Success ?: return null
        Diag.log("resolve", "$videoId needed the signed-in account — age-restricted, most likely")
        // The two responses hold different halves of one video and neither is enough alone: the
        // signed-in TV client supplies streams and NO readable metadata, while the anonymous
        // refusal we just got supplies the title, author and length and no streams. Joining them
        // is what makes an age-restricted video showable as well as playable.
        val describedBy = (anonymous as? PlayerResult.Unplayable)?.details
        if (signedIn.details == null && describedBy != null) {
            Diag.log("resolve", "$videoId described by the refused anonymous response: \"${describedBy.title}\"")
            return signedIn.copy(details = describedBy)
        }
        return signedIn
    }

    /** The parsed anonymous response, refusals included — see [playerFor] for why they matter. */
    private suspend fun anonymousPlayer(videoId: String): PlayerResult? {
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
                parsed
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
