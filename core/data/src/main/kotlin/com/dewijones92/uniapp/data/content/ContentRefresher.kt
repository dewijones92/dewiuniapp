package com.dewijones92.uniapp.data.content

/**
 * The one seam that finds new content across both pillars. It asks every
 * [SubscriptionItemsSource] for its current items, diffs each source against
 * the [SeenItemsTracker], and returns only sources that have genuinely new
 * items — the podcast-vs-video specifics live entirely in the adapters, so the
 * background worker and the in-app bell share one detection path (strictly DRY).
 *
 * A source that fails to fetch is skipped, not fatal: one broken feed never
 * hides new content from the others.
 */
public class ContentRefresher(
    private val sources: List<SubscriptionItemsSource>,
    private val tracker: SeenItemsTracker,
) {
    /** New items grouped by source; empty when nothing is new. Sources with no new items are omitted. */
    public suspend fun findNewContent(): List<SourceUpdate> =
        sources
            .flatMap { source -> runCatching { source.currentItems() }.getOrDefault(emptyList()) }
            .mapNotNull { update ->
                val fresh = tracker.newItems(update.source.id, update.items)
                tracker.markSeen(update.source.id, update.items)
                fresh.takeIf { it.isNotEmpty() }?.let { SourceUpdate(update.source, it) }
            }
}
