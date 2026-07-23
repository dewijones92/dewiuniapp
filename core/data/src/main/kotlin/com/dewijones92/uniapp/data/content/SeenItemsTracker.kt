package com.dewijones92.uniapp.data.content

import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId

/**
 * Remembers which items have already been "seen" for a source, so the same
 * upload or episode is only ever surfaced once.
 *
 * The first time a source is queried its whole current set is treated as
 * already seen and [newItems] returns nothing — no flood on first run, and no
 * flood the first time a freshly-added subscription is refreshed. Only items
 * that appear *after* a source is known count as new.
 *
 * The mechanism is pillar-agnostic (keyed by [MediaItemId][com.dewijones92.uniapp.domain.MediaItemId]);
 * a single implementation serves both the in-app bell and the background
 * notifications, each under its own storage namespace since they track
 * independent "unread" states.
 */
public interface SeenItemsTracker {

    /** The items in [items] not yet seen for [source]; empty on a source's first query. */
    public fun newItems(source: SourceId, items: List<MediaItem>): List<MediaItem>

    /** Marks [items] seen for [source] so they stop counting as new. */
    public fun markSeen(source: SourceId, items: List<MediaItem>)
}
