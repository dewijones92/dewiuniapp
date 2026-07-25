package com.dewijones92.totum.data.content

import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource

/**
 * Items belonging to one [source]. Used both ways in the refresh seam: an
 * adapter returns the *current* items for a source, and [ContentRefresher]
 * returns the *new-since-last-seen* subset in the same shape — one type, both
 * pillars.
 */
public data class SourceUpdate(
    public val source: MediaSource,
    public val items: List<MediaItem>,
)
