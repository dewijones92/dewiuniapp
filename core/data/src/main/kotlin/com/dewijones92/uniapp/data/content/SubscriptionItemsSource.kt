package com.dewijones92.uniapp.data.content

/**
 * One pillar's contribution to the unified refresh: the current items for every
 * subscription it owns, grouped by source. Podcasts re-fetch their RSS feeds;
 * YouTube reads the account subscriptions feed. [ContentRefresher] diffs the
 * result against what's already been seen — an adapter only reports "what's
 * there now", never "what's new".
 */
public fun interface SubscriptionItemsSource {
    /** Current items per subscribed source (newest-first within each). */
    public suspend fun currentItems(): List<SourceUpdate>
}
