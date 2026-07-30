package com.dewijones92.totum.data.group

import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource

/**
 * Recent items from ONE source, whichever pillar it belongs to — the seam a group feed
 * fans out over.
 *
 * Takes a [MediaSource], not a `SourceId`, and that is the whole point: `MediaSource` is
 * sealed, so [RoutedSourceItems] routes in an exhaustive `when` that stops compiling if a
 * third pillar appears. Passing an id would have forced the router to guess a pillar from
 * the URL — the exact mistake that once made a Shorts URL download as a video and queue as
 * a podcast enclosure, from two rules that disagreed.
 */
public fun interface SourceItems {
    /** Newest first. An empty list means "nothing to show", never "it failed" — see below. */
    public suspend fun itemsFor(source: MediaSource): List<MediaItem>
}

/**
 * Sends each source to its pillar's fetcher. The one place a group feed knows pillars
 * exist, mirroring `RoutedDownloadStrategy` — a third pillar cannot be added without this
 * `when` failing to compile.
 */
public class RoutedSourceItems(
    private val video: SourceItems,
    private val podcast: SourceItems,
) : SourceItems {
    override suspend fun itemsFor(source: MediaSource): List<MediaItem> = when (source) {
        is MediaSource.VideoChannel -> video.itemsFor(source)
        is MediaSource.PodcastFeed -> podcast.itemsFor(source)
    }
}
