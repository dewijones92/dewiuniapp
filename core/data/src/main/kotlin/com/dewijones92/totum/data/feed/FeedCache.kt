package com.dewijones92.totum.data.feed

import com.dewijones92.totum.domain.MediaItem

/**
 * The last-known contents of a feed, so a screen has something to show before the network
 * answers.
 *
 * Read-then-refresh, not read-instead-of: the cache is what fills the gap, never the source of
 * truth. Measured 2026-07-31, the gap it fills is about 1.2 seconds of empty Videos tab on
 * every single launch.
 *
 * Keyed by a plain string so one seam serves account feeds and groups alike — the caller names
 * its own feed, and nothing here needs to know the difference.
 */
public interface FeedCache {

    /** Empty when nothing has been cached for [feedKey] yet; never an error. */
    public suspend fun items(feedKey: String): List<MediaItem>

    /** Replaces everything cached for [feedKey]. A feed is a snapshot, not an accumulation. */
    public suspend fun save(feedKey: String, items: List<MediaItem>)
}

/** A [FeedCache] that forgets everything, for tests and previews. */
public object NoOpFeedCache : FeedCache {
    override suspend fun items(feedKey: String): List<MediaItem> = emptyList()

    override suspend fun save(feedKey: String, items: List<MediaItem>): Unit = Unit
}
