package com.dewijones92.totum.innertube.feeds

/**
 * Parses a YouTube clock string — "s", "m:ss" or "h:mm:ss" — into total
 * seconds. Each field carries into the next at base 60. Returns null if any
 * field isn't a number or there are more than three fields. Shared by every
 * InnerTube parser that reads a duration off a thumbnail overlay (feed tiles,
 * related-video lockups), so the format lives in one place.
 */
internal fun parseClockToSeconds(clock: String): Long? {
    val parts = clock.trim().split(":")
    if (parts.isEmpty() || parts.size > MAX_CLOCK_FIELDS) return null
    return parts.fold(0L) { acc, part -> acc * SECONDS_PER_MINUTE + (part.toLongOrNull() ?: return null) }
}

private const val SECONDS_PER_MINUTE = 60L
private const val MAX_CLOCK_FIELDS = 3

// YouTube renders a tile's published date as relative text ("2 days ago",
// "Streamed 3 hours ago") or, for premieres, "Premiered ...". This picks that
// metadata part out from views/other lines. Shared by the feed and related parsers.
private val PUBLISHED_HINT = Regex("""\bago\b|Streamed|Premiered""", RegexOption.IGNORE_CASE)

internal fun String.looksLikePublished(): Boolean = PUBLISHED_HINT.containsMatchIn(this)

// The view-count metadata part: "1.2M views", "No views", and the live variant
// "12K watching". Matched by shape rather than position, because YouTube reorders the
// metadata parts between tile types and a positional read silently picks the wrong one.
private val VIEWS_HINT = Regex("""\bviews?\b|\bwatching\b""", RegexOption.IGNORE_CASE)

internal fun String.looksLikeViews(): Boolean = VIEWS_HINT.containsMatchIn(this)

// A membership badge: "Members only", "Members first". Same reasoning — matched by text,
// since the badge renderers differ between the lockup and classic tile shapes.
private val MEMBERS_HINT = Regex("""\bmembers\b""", RegexOption.IGNORE_CASE)

internal fun String.looksLikeMembers(): Boolean = MEMBERS_HINT.containsMatchIn(this)
