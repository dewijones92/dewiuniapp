package com.dewijones92.totum.video

import com.dewijones92.totum.common.HttpUrl
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
    )

    /** Null when the video can't be resolved (private, removed, geo-blocked, …). */
    suspend fun resolve(watchUrl: HttpUrl, sourceId: SourceId): Resolved? {
        val metadata = (engine.extract(watchUrl) as? ExtractionResult.Success)?.metadata ?: return null
        // Default stream stays the best muxed format (one stream, reliable, data-friendly);
        // the quality menu offers higher, merged qualities on demand.
        val streamUrl = metadata.bestPlayableFormat()?.url?.let(HttpUrl::parse) ?: return null
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
            qualities = metadata.videoQualities(codecSupport),
            audioOnlyUrl = metadata.bestAudioUrl(),
            playbackTrackingUrl = metadata.playbackTrackingUrl,
            watchtimeTrackingUrl = metadata.watchtimeTrackingUrl,
        )
    }
}
