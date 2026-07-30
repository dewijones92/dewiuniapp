package com.dewijones92.totum.data.group

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceGroup
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * One group's members read as a single newest-first feed.
 *
 * Per-member rather than filtering the account's subscriptions feed, which would be a
 * single request: that feed is a *sample* of recent uploads across everything you follow,
 * so a quiet channel in the group can be absent from it entirely, and it does not reliably
 * carry shorts or live. Dewi asked for "the live stream, short, everything" from these
 * particular channels, and the only way to get everything from a channel is to ask that
 * channel.
 *
 * Members are fetched CONCURRENTLY, because the cost is a round trip each and a group of
 * ten read one at a time would be ten round trips of waiting.
 *
 * A member that fails contributes nothing and does not fail the group. A group is a view
 * over several sources and most of it is still worth reading when one is unreachable —
 * but it is logged, because "this channel has nothing new" and "this channel could not be
 * reached" look identical in a merged list and only one of them is a problem.
 */
public class GroupFeed(private val items: SourceItems) {
    public suspend fun itemsFor(group: SourceGroup): List<MediaItem> = coroutineScope {
        val members = group.members
        Diag.log("group", "loading \"${group.name}\" from ${members.size} source(s)")
        val fetched = members
            .map { source -> async { itemsForMember(source) } }
            .flatMap { it.await() }
        // Undated items sort last rather than being dropped: a channel that gives no date
        // is still worth reading, just not worth claiming a position among the dated ones.
        val merged = fetched
            .distinctBy { it.id }
            .sortedByDescending { it.publishedAt }
        Diag.log(
            "group",
            "\"${group.name}\" merged to ${merged.size} item(s) from ${fetched.size} fetched",
        )
        merged
    }

    private suspend fun itemsForMember(source: MediaSource): List<MediaItem> =
        runCatching { items.itemsFor(source) }
            .onFailure { Diag.warn("group", "\"${source.title}\" could not be read", it) }
            .getOrDefault(emptyList())
}
