package com.dewijones92.uniapp.domain

import kotlin.time.Duration

/** A stretch of media the user doesn't want to hear (sponsor read, self-promo…). */
public data class SkipSegment(
    val start: Duration,
    val end: Duration,
) {
    init {
        require(!start.isNegative()) { "start must not be negative" }
        require(end > start) { "end must be after start" }
    }
}

/**
 * Where playback should jump to if [position] is inside a segment; null when
 * it isn't. Overlapping/adjacent segments are honoured by re-checking the
 * landing position against the remaining segments.
 */
public fun List<SkipSegment>.skipTargetFor(position: Duration): Duration? {
    var target: Duration? = null
    var current = position
    // Bounded: each hop consumes at least one segment.
    sortedBy { it.start }.forEach { segment ->
        if (current >= segment.start && current < segment.end) {
            target = segment.end
            current = segment.end
        }
    }
    return target
}
