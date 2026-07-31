package com.dewijones92.totum.video

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.innertube.player.PlayableFormat
import com.dewijones92.totum.innertube.player.PlayerDetails
import com.dewijones92.totum.innertube.player.StreamingData
import com.dewijones92.totum.sabr.SabrFormat
import com.dewijones92.totum.sabr.SabrSession
import com.dewijones92.totum.sabr.SabrSessions

/**
 * Turns a `/player` response into a registered SABR session and the URLs that play it.
 *
 * This is the shipping edge of the SABR work. A `/player` call answers in about 150ms with a
 * full ladder where a yt-dlp extraction costs 2-4 seconds on a phone, and SABR is the only way
 * to fetch the bytes those URLs describe — measured 2026-07-31, a plain ranged GET of an
 * ANDROID-client stream URL serves its first megabyte and then 403s forever.
 *
 * Returns null rather than guessing whenever the response is not completely usable, and the
 * caller extracts as it always has. Every reason is logged, because "SABR did not happen" with
 * no explanation would be the hardest kind of bug to chase.
 */
internal object SabrResolve {

    /** A playable pair of `sabr://` URLs, plus the details that describe the video. */
    data class Resolved(
        val details: PlayerDetails,
        val videoUrl: HttpUrl?,
        val audioUrl: HttpUrl,
    )

    fun prepare(videoId: String, streaming: StreamingData, details: PlayerDetails?): Resolved? {
        val endpoint = streaming.serverAbrStreamingUrl?.value ?: return refuse(videoId, "no SABR endpoint")
        val config = streaming.ustreamerConfig ?: return refuse(videoId, "no ustreamer config")
        val known = details ?: return refuse(videoId, "no videoDetails")

        val audio = streaming.formats.bestAudio() ?: return refuse(videoId, "no identifiable audio format")
        // AUDIO ONLY, deliberately, and this is the honest limit of the first version.
        //
        // Audio plays: proven on the emulator (PLAYED 1187ms of itag 140) and on the desktop,
        // where 152623 fetched bytes decoded to 9.99s of 48kHz stereo. Video does NOT: itag 137
        // is served and the bytes arrive, but ExoPlayer rejects them with "Invalid NAL length"
        // and contentIsMalformed — it reads valid mp4 and then meets a gap, so the runs SABR
        // returns for video are not byte-contiguous in the order they arrive and the stream
        // needs to hold them until they are. Shipping a video path that decodes to corruption
        // would be worse than not shipping one.
        val video: PlayableFormat? = null

        SabrSessions.register(
            videoId,
            SabrSession(endpoint, config, audio.toSabrFormat(), video?.toSabrFormat()),
        )
        val audioUrl = SabrSessions.uriFor(videoId, audio.itag)?.let(HttpUrl::parse)
            ?: return refuse(videoId, "could not build a marked endpoint URL")
        Diag.log(
            "sabr",
            "prepared $videoId — audio itag ${audio.itag}, video itag ${video?.itag ?: "none"}",
        )
        return Resolved(
            details = known,
            videoUrl = video?.let { SabrSessions.uriFor(videoId, it.itag)?.let(HttpUrl::parse) },
            audioUrl = audioUrl,
        )
    }

    /**
     * The best audio SABR will actually serve.
     *
     * `lastModified` is required because it identifies the format, and itag 139 is excluded by
     * name: probing every audio format on 2026-07-31, 139 answered `sabr.no_audio_selected`
     * while 140, 249, 251, 599 and 600 all served. A listed format is not necessarily an
     * obtainable one, and choosing purely by bitrate would pick a refused one soon enough.
     */
    private fun List<PlayableFormat>.bestAudio(): PlayableFormat? =
        filter { it.mimeType?.startsWith("audio/") == true }
            .filter { it.lastModified != null && it.itag !in REFUSED_ITAGS }
            .maxByOrNull { it.bitrate ?: 0 }

    /**
     * The best video SABR will actually serve. **Unused until video assembly is contiguous**,
     * kept because the finding below cost a probe of every format and would otherwise be lost.
     *
     * Probed every video format of a real video on 2026-07-31, and the pattern is total:
     *
     * | Container | Result |
     * |---|---|
     * | `video/webm` (VP9) — itags 313, 271, 248, 247, 244, 243, 242, 278, 598 | **every one refused** |
     * | `video/mp4` (H.264 and AV1) — 137, 400, 399, 398, 397, 396, 136, 135, 134, 133, 160, 394 | served |
     *
     * VP9 answers `sabr.no_video_selected` without exception, so mp4 is required. Note this is
     * video-only: the audio track we use is `audio/webm` opus and serves perfectly.
     *
     * Capped at 1080p because the two mp4 refusals were both outside the ordinary range (2160p
     * AV1, and a 240p AV1 oddity). 1080p is also plenty on a phone, so the cap costs nothing
     * and avoids a class of failure rather than guessing at its edges.
     */
    @Suppress("unused")
    private fun List<PlayableFormat>.bestVideo(): PlayableFormat? =
        filter { it.mimeType?.contains("mp4") == true && it.height != null }
            .filter { it.lastModified != null && (it.height ?: 0) <= MAX_SABR_HEIGHT }
            .maxByOrNull { it.height ?: 0 }

    private fun PlayableFormat.toSabrFormat() = SabrFormat(itag, lastModified!!, xtags)

    private fun refuse(videoId: String, why: String): Resolved? {
        Diag.log("sabr", "not using SABR for $videoId: $why — extracting instead")
        return null
    }

    /** Formats YouTube lists but will not serve over SABR. */
    private val REFUSED_ITAGS = setOf(139)

    /** Above this, mp4 formats started being refused too; and it is plenty on a phone. */
    private const val MAX_SABR_HEIGHT = 1080
}
