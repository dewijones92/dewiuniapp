package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SkipSegment
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import com.dewijones92.uniapp.ytdlp.bestPlayableFormat
import kotlin.time.Duration.Companion.seconds

/**
 * Turns a video watch URL into a directly-playable [MediaItem] plus its
 * SponsorBlock segments, resolving the stream through the engine. Shared by
 * search and channel playback so the resolve-then-play logic lives once.
 */
class VideoResolver(
    private val engine: YtDlpEngine,
    private val skipSegments: SkipSegmentSource,
) {
    data class Resolved(val item: MediaItem, val skipSegments: List<SkipSegment>)

    /** Null when the video can't be resolved (private, removed, geo-blocked, …). */
    suspend fun resolve(watchUrl: HttpUrl, sourceId: SourceId): Resolved? {
        val metadata = (engine.extract(watchUrl) as? ExtractionResult.Success)?.metadata ?: return null
        val streamUrl = metadata.bestPlayableFormat()?.url?.let(HttpUrl::parse) ?: return null
        return Resolved(
            item = MediaItem(
                id = MediaItemId(metadata.id),
                sourceId = sourceId,
                title = metadata.title,
                publishedAt = null,
                duration = metadata.durationSeconds?.seconds,
                author = metadata.uploader,
                thumbnailUrl = metadata.thumbnailUrl?.let(HttpUrl::parse),
                mediaUrl = streamUrl,
            ),
            skipSegments = skipSegments.segmentsFor(metadata.id),
        )
    }
}
