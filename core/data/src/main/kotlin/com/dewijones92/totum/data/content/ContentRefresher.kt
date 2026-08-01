package com.dewijones92.totum.data.content

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.Vitals

/**
 * The one seam that finds new content across both pillars. It asks every
 * [SubscriptionItemsSource] for its current items and diffs each source against
 * the [SeenItemsTracker] — the podcast-vs-video specifics live entirely in the
 * adapters, so the background worker and the in-app bell share one detection
 * path (strictly DRY).
 *
 * Detection is decoupled from committing: [findNewContent] reports what's new
 * without advancing the seen-state, and the caller calls [NewContentBatch.markDelivered]
 * only once the user has actually been notified. That way a run that can't
 * deliver (notification permission not yet granted, a transient failure) doesn't
 * silently consume new items into the seen-set and lose them forever.
 *
 * A source that fails to fetch is skipped, not fatal: one broken feed never
 * hides new content from the others, and — because it never reaches
 * [markDelivered][NewContentBatch.markDelivered] — its items are retried next run.
 *
 * Skipped, but no longer SILENTLY. This ran every six hours in the background and swallowed
 * whatever a source threw into an empty list, so a pillar that had stopped working — expired
 * YouTube auth, say — simply never contributed new content again, with nothing anywhere to say
 * so. "Why did I not get told about that episode?" was unanswerable, which for a background job
 * nobody watches is the worst place for it to be true.
 */
public class ContentRefresher(
    private val sources: List<SubscriptionItemsSource>,
    private val tracker: SeenItemsTracker,
) {
    /** New items grouped by source, plus a handle to commit them once delivered. */
    public suspend fun findNewContent(): NewContentBatch {
        var failed = 0
        val current = sources.flatMap { source ->
            runCatching { source.currentItems() }
                .onFailure { error ->
                    failed++
                    Vitals.add("content.sourceFailures")
                    // WITH the throwable: "a source failed" is not actionable, and the cause of
                    // a background failure is never guessable after the fact.
                    Diag.warn("content", "a subscription source could not be read", error)
                }
                .getOrDefault(emptyList())
        }
        val fresh = current.mapNotNull { update ->
            tracker.newItems(update.source.id, update.items)
                .takeIf { it.isNotEmpty() }
                ?.let { SourceUpdate(update.source, it) }
        }
        // One line per run, and it runs every six hours — so this is four lines a day and the
        // only evidence that the thing ran at all.
        Vitals.add("content.refreshes")
        Diag.log(
            "content",
            "checked ${sources.size} source(s) ($failed failed): ${current.size} with items, " +
                "${fresh.sumOf { it.items.size }} new across ${fresh.size} source(s)",
        )
        return NewContentBatch(fresh, current, tracker)
    }
}

/**
 * The outcome of one refresh: [newContent] is the genuinely-new items (empty when
 * nothing is new). [markDelivered] advances the seen-state to everything currently
 * present — call it only after the user has actually been shown [newContent], so a
 * failed or permission-blocked delivery leaves the items to be found again.
 */
public class NewContentBatch internal constructor(
    public val newContent: List<SourceUpdate>,
    private val currentItems: List<SourceUpdate>,
    private val tracker: SeenItemsTracker,
) {
    public fun markDelivered() {
        currentItems.forEach { tracker.markSeen(it.source.id, it.items) }
    }
}
