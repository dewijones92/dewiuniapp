package com.dewijones92.totum.video

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.SubtitleTrack
import com.dewijones92.totum.common.Vitals
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.domain.Chapter
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.SkipSegment
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.ytdlp.ExtractionResult
import com.dewijones92.totum.ytdlp.YtDlpEngine
import com.dewijones92.totum.ytdlp.bestPlayableFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/**
 * Turns a video watch URL into a directly-playable [MediaItem] plus its
 * SponsorBlock segments, resolving the stream through the engine. Shared by
 * search and channel playback so the resolve-then-play logic lives once.
 */
class VideoResolver(
    private val engine: YtDlpEngine,
    private val skipSegments: SkipSegmentSource,
    /** Keeps undecodable streams out of the quality ladder (see [VideoCodecSupport]). */
    private val codecSupport: VideoCodecSupport = VideoCodecSupport.Permissive,
    /**
     * Asked only when yt-dlp comes back with a degraded ladder — see [betterQualities].
     * Null disables the fallback entirely, which is what tests and previews want.
     */
    private val playerStreams: PlayerStreams? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * The most recently resolved video, reused until it goes stale.
     *
     * Extraction measured 7.2 SECONDS on a real device, and because videos resolve
     * just-in-time that was seven seconds of silence between every track. Prefetching only
     * helps if the answer survives to be used, hence this.
     *
     * Exactly one entry: what is being prefetched is the ONE item playing next, and a larger
     * cache would hold URLs long enough to expire — which is the failure this must not cause.
     * The single entry is also why the TTL stays short: a stale URL 403s, and
     * ExpiredStreamRecovery only re-resolves, so serving one from here would loop.
     */
    private var cached: Pair<HttpUrl, Resolved>? = null
    private var cachedAt = 0L

    /**
     * The extraction currently running, if any, and what it is for. Concurrent callers for
     * the same video await this rather than starting a second one.
     */
    private var inFlight: Pair<HttpUrl, CompletableDeferred<Resolved?>>? = null
    private val inFlightMutex = Mutex()

    data class Resolved(
        val item: MediaItem,
        val skipSegments: List<SkipSegment>,
        /** Selectable streaming qualities, highest first; empty for audio-only. */
        val qualities: List<VideoQuality> = emptyList(),
        /** Best audio-only stream, for "Listen" mode; null if none is available. */
        val audioOnlyUrl: HttpUrl? = null,
        /** YouTube watch-progress stats URLs (from yt-dlp's player response), null for non-YouTube. */
        val playbackTrackingUrl: String? = null,
        val watchtimeTrackingUrl: String? = null,
        /** Renderable caption tracks; empty when the video has none we can use. */
        val subtitles: List<SubtitleTrack> = emptyList(),
    )

    /**
     * Null when the video can't be resolved (private, removed, geo-blocked, …).
     *
     * Each way of failing is logged distinctly. They used to share one silent `return
     * null`, so "it wouldn't play" gave no clue whether extraction failed or succeeded
     * with nothing playable in it — a difference between a YouTube change and a codec
     * problem.
     */
    suspend fun resolve(watchUrl: HttpUrl, sourceId: SourceId, asked: String = "play"): Resolved? {
        // Share one extraction between concurrent askers.
        //
        // The cache only helps a caller arriving AFTER the first has finished. A real report
        // (0.1.226) shows the same video resolved twice, overlapping: the second started
        // while the first was still running, saw an empty cache, and did the whole 10s
        // extraction again. Two callers wanting the same video at the same moment should
        // wait on one answer.
        //
        // The FIRST caller does the work on its own dispatcher and the others await it —
        // rather than handing it to a scope this class owns, which would take the work off
        // whatever dispatcher the caller (or a test) is driving.
        val joined = inFlightMutex.withLock {
            inFlight?.takeIf { it.first == watchUrl && it.second.isActive }?.second
        }
        if (joined != null) {
            Diag.log(
                "resolve",
                "joining the extraction already running for " +
                    "${watchUrl.value.takeLast(ID_CHARS)} ($asked)",
            )
            return joined.await()
        }

        val mine = CompletableDeferred<Resolved?>()
        inFlightMutex.withLock { inFlight = watchUrl to mine }
        return try {
            extractAndCache(watchUrl, sourceId, asked).also(mine::complete)
        } finally {
            // Always settled, never left hanging: whoever joined must not wait forever
            // because the caller that started the work threw or was cancelled.
            mine.complete(null)
            inFlightMutex.withLock { if (inFlight?.second === mine) inFlight = null }
        }
    }

    private suspend fun extractAndCache(watchUrl: HttpUrl, sourceId: SourceId, asked: String): Resolved? {
        cached?.takeIf { it.first == watchUrl && now() - cachedAt < CACHE_TTL_MS }?.let { (_, hit) ->
            // Kept, not consumed. It used to be cleared on first use, which was right when an
            // extraction cost ~1.7s and the cache existed only to hand a prefetched result to
            // the next play. With a JS runtime an extraction costs 10-14s on a real phone, so
            // every replay, seek-triggered re-resolve and quality change was paying it again:
            // one video was extracted FOUR times in 30 seconds on Dewi's Pixel.
            Diag.log("resolve", "cache hit for ${watchUrl.value.takeLast(ID_CHARS)} ($asked), skipped extraction")
            return hit
        }
        val startedAt = now()
        val extraction = engine.extract(watchUrl)
        val metadata = (extraction as? ExtractionResult.Success)?.metadata ?: run {
            Vitals.add("resolve.extractFailures")
            Diag.warn("resolve", "extract failed for ${watchUrl.value} ($asked): $extraction")
            return null
        }
        // Default stream stays the best muxed format (one stream, reliable, data-friendly);
        // the quality menu offers higher, merged qualities on demand.
        val streamUrl = metadata.bestPlayableFormat()?.url?.let(HttpUrl::parse) ?: run {
            Vitals.add("resolve.noPlayableFormat")
            Diag.warn(
                "resolve",
                "no playable format for ${metadata.id} (${metadata.formats.size} formats offered)",
            )
            return null
        }
        val qualities = betterQualities(metadata.id, metadata.videoQualities(codecSupport))
        Vitals.add("resolve.successes")
        Diag.log(
            "resolve",
            "${metadata.id} in ${now() - startedAt}ms for $asked — " +
                "${qualities.size} qualities, ${metadata.subtitles.size} subtitle tracks, " +
                "audioOnly=${metadata.bestAudioUrl() != null}",
        )
        val resolved = Resolved(
            item = MediaItem(
                id = MediaItemId(metadata.id),
                sourceId = sourceId,
                title = metadata.title,
                publishedAt = null,
                duration = metadata.durationSeconds?.seconds,
                author = metadata.uploader,
                description = metadata.description,
                thumbnailUrl = metadata.thumbnailUrl?.let(HttpUrl::parse),
                mediaUrl = streamUrl,
                chapters = metadata.chapters.mapNotNull { chapter ->
                    val title = chapter.title.trim().ifBlank { null } ?: return@mapNotNull null
                    val start = chapter.startSeconds.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
                    Chapter(start.seconds, title)
                },
            ),
            skipSegments = skipSegments.segmentsFor(metadata.id),
            qualities = qualities,
            audioOnlyUrl = metadata.bestAudioUrl(),
            playbackTrackingUrl = metadata.playbackTrackingUrl,
            watchtimeTrackingUrl = metadata.watchtimeTrackingUrl,
            subtitles = metadata.subtitles,
        )
        // Remembered on EVERY resolve, not only when prefetched: a replay, a seek that
        // forces a re-resolve or a quality change all ask for the same video again, and each
        // one used to pay the full extraction.
        cached = watchUrl to resolved
        cachedAt = now()
        return resolved
    }

    /**
     * Replaces a degraded ladder with a better one, when YouTube will give us one.
     *
     * A single 360p quality is the signature of yt-dlp having lost the rest: it has
     * deprecated extraction without a JavaScript runtime, and Chaquopy cannot give it one,
     * so on a phone it quietly drops formats. Asking `/player` ourselves as the ANDROID
     * client gets them back — 32 formats to 1080p where yt-dlp offered one at 360p, with no
     * `n` parameter to decipher and so no runtime implied. Proven on the same video from
     * which yt-dlp on a laptop WITH node also gets the full ladder, which is what identified
     * the missing runtime as the cause.
     *
     * Only on the degraded case, deliberately. yt-dlp handles a great deal this does not —
     * age gates, region locks, signature ciphers, non-YouTube sources — so it stays the
     * primary and this is the second opinion, asked when the first is visibly poor.
     */
    private suspend fun betterQualities(id: String, qualities: List<VideoQuality>): List<VideoQuality> {
        val fallback = playerStreams ?: return qualities.also { reportIfDegraded(id, it) }
        val best = qualities.maxOfOrNull { it.height } ?: 0
        if (qualities.size > 1 || best > DEGRADED_HEIGHT) return qualities
        Diag.log("resolve", "$id offered one quality at ${best}p — asking YouTube directly")

        val streams = fallback.streamsFor(id) ?: return qualities.also { reportIfDegraded(id, it) }
        val better = streams.videoQualities()
        val betterBest = better.maxOfOrNull { it.height } ?: 0
        if (betterBest <= best) {
            Diag.log("resolve", "$id: the direct ask offered no better (${betterBest}p) — keeping yt-dlp's")
            reportIfDegraded(id, qualities)
            return qualities
        }
        Vitals.add("resolve.playerFallbackWins")
        Diag.log("resolve", "$id: direct ask gave ${better.size} qualities to ${betterBest}p, up from ${best}p")
        return better
    }

    /**
     * Says so when a video came back with nothing but the legacy 360p stream.
     *
     * That is the signature of YouTube serving a video SABR-only: the higher formats are
     * listed in the player response but carry no URL, so yt-dlp drops them and format 18 —
     * the old progressive muxed stream — is all that survives. Today it happens on
     * made-for-kids videos; the experiment has been widening, and the point of counting it
     * is to learn that from a diagnostics report rather than from Dewi noticing a video
     * looks soft. See docs/todos/sabr-streaming.md.
     */
    private fun reportIfDegraded(id: String, qualities: List<VideoQuality>) {
        val best = qualities.maxOfOrNull { it.height } ?: return
        if (qualities.size > 1 || best > DEGRADED_HEIGHT) return
        Vitals.add("resolve.sabrDegraded")
        Diag.warn(
            "resolve",
            "$id offered ONE quality at ${best}p — YouTube is almost certainly serving this " +
                "SABR-only, so the higher formats exist but have no URL to fetch",
        )
    }

    /**
     * Resolves [watchUrl] and keeps the answer for the next [resolve] of the same URL.
     *
     * Called shortly before the current item ends, so the seven seconds of extraction happen
     * while something is still playing rather than in the silence afterwards. Failure is
     * deliberately swallowed: a prefetch that does not work must cost nothing, and the real
     * resolve will run and report properly when the item is actually needed.
     */
    suspend fun prefetch(watchUrl: HttpUrl, sourceId: SourceId) {
        if (cached?.first == watchUrl && now() - cachedAt < CACHE_TTL_MS) return
        Diag.log("resolve", "prefetching ${watchUrl.value.takeLast(ID_CHARS)}")
        val resolved = runCatching { resolve(watchUrl, sourceId, asked = "prefetch") }.getOrNull() ?: run {
            Diag.log("resolve", "prefetch produced nothing; the real resolve will try again")
            return
        }
        cached = watchUrl to resolved
        cachedAt = now()
    }

    private companion object {
        /**
         * Far below a signed URL's lifetime. A prefetch is used within a minute or two, so this
         * only has to outlive that — holding one longer risks handing back a URL that has already
         * expired, which is a worse failure than the wait it saves.
         */
        const val CACHE_TTL_MS = 10 * 60 * 1000L

        /** Enough of a watch URL to recognise the video in a log line. */
        const val ID_CHARS = 11

        /** Format 18's height — the only stream that survives a SABR-only response. */
        const val DEGRADED_HEIGHT = 360
    }
}
