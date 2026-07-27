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
) {
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
    suspend fun resolve(watchUrl: HttpUrl, sourceId: SourceId): Resolved? {
        val startedAt = System.currentTimeMillis()
        val extraction = engine.extract(watchUrl)
        val metadata = (extraction as? ExtractionResult.Success)?.metadata ?: run {
            Vitals.add("resolve.extractFailures")
            Diag.warn("resolve", "extract failed for ${watchUrl.value}: $extraction")
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
        val qualities = metadata.videoQualities(codecSupport)
        Vitals.add("resolve.successes")
        Diag.log(
            "resolve",
            "${metadata.id} in ${System.currentTimeMillis() - startedAt}ms — " +
                "${qualities.size} qualities, ${metadata.subtitles.size} subtitle tracks, " +
                "audioOnly=${metadata.bestAudioUrl() != null}",
        )
        return Resolved(
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
    }
}
