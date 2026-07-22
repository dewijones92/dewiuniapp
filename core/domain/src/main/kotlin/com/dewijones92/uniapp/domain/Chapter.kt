package com.dewijones92.uniapp.domain

import kotlin.time.Duration

/**
 * A named point in a piece of media — a video chapter (from yt-dlp) or a podcast
 * chapter (from the feed). Just a start and a title: enough to mark the seek bar
 * and offer a tap-to-jump list. Pillar-agnostic, so one seam serves both.
 */
public data class Chapter(val start: Duration, val title: String) {
    init {
        require(start >= Duration.ZERO) { "Chapter start must not be negative" }
        require(title.isNotBlank()) { "Chapter title must not be blank" }
    }
}
