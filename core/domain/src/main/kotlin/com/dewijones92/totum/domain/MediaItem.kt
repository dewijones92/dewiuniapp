package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl
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
    /**
     * Human published date as the source renders it (e.g. YouTube's "2 days
     * ago"), for sources that give relative text rather than an absolute
     * [publishedAt]. The UI prefers this when set, else formats [publishedAt].
     */
    val publishedText: String? = null,
    val duration: Duration?,
    /** Who made it: podcast/feed name or channel/uploader. Shown as the artist line. */
    val author: String? = null,
    val description: String? = null,
    val thumbnailUrl: HttpUrl? = null,
    /** Where the playable media lives (podcast enclosure, resolved stream); null until known. */
    val mediaUrl: HttpUrl? = null,
    /**
     * How many have watched/listened, as the source renders it ("1.2M views"), or null when
     * the source does not say. Text rather than a number because YouTube only ever gives
     * text, and re-formatting a parsed approximation would say the same thing less
     * accurately; sources that DO give a number format it with [formatViewCount] so every
     * list reads the same either way.
     */
    val viewsText: String? = null,
    /**
     * Behind a channel membership. Worth showing rather than discovering at play time: a
     * members-only video looks identical in a list and then fails with "Join this channel
     * to get access" — three of them sat unexplained in a real download queue.
     */
    val membersOnly: Boolean = false,
    /** Whether this is a normal video, a live stream or a Short — for feed tagging. */
    val contentKind: MediaContentKind = MediaContentKind.STANDARD,
    /** Named points along the media (video/podcast chapters), earliest first; empty if none. */
    val chapters: List<Chapter> = emptyList(),
) {
    init {
        require(duration == null || duration.isPositive()) { "duration must be positive when present" }
    }
}

/**
 * "1.2M views" from a raw count — YouTube's own shape, so a yt-dlp-sourced row and an
 * InnerTube-sourced one read identically in the same list.
 */
public fun formatViewCount(views: Long): String = when {
    views < THOUSAND -> "$views views"
    views < MILLION -> "${(views / THOUSAND.toDouble()).trimmed()}K views"
    views < BILLION -> "${(views / MILLION.toDouble()).trimmed()}M views"
    else -> "${(views / BILLION.toDouble()).trimmed()}B views"
}

/** One decimal place, but not a trailing ".0" — YouTube writes "12K", not "12.0K". */
private fun Double.trimmed(): String {
    val oneDecimal = kotlin.math.floor(this * DECIMAL_PLACE) / DECIMAL_PLACE
    return if (oneDecimal == kotlin.math.floor(oneDecimal)) oneDecimal.toLong().toString() else oneDecimal.toString()
}

/** Truncating to one decimal: never rounds a count UP, so "1M views" is never a lie. */
private const val DECIMAL_PLACE = 10.0

private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
private const val BILLION = 1_000_000_000L
