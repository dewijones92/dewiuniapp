package com.dewijones92.uniapp.domain

import com.dewijones92.uniapp.common.HttpUrl
import java.time.Instant
import kotlin.time.Duration

/** Stable identity of a [MediaItem] within its source; never blank. */
@JvmInline
public value class MediaItemId(public val value: String) {
    init {
        require(value.isNotBlank()) { "MediaItemId must not be blank" }
    }
}

/**
 * One playable thing — a video or a podcast episode. Which pillar it belongs
 * to is determined by the [MediaSource] behind [sourceId], not by this type:
 * playback, queueing, and downloads are identical for both.
 */
public data class MediaItem(
    val id: MediaItemId,
    val sourceId: SourceId,
    val title: String,
    val publishedAt: Instant?,
    val duration: Duration?,
    val description: String? = null,
    val thumbnailUrl: HttpUrl? = null,
    /** Where the playable media lives (podcast enclosure, resolved stream); null until known. */
    val mediaUrl: HttpUrl? = null,
) {
    init {
        require(duration == null || duration.isPositive()) { "duration must be positive when present" }
    }
}
