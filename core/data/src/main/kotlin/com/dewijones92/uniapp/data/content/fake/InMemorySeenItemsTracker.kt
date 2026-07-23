package com.dewijones92.uniapp.data.content.fake

import com.dewijones92.uniapp.data.content.SeenItemsTracker
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId

/** In-memory [SeenItemsTracker] for tests and previews; state lives for the instance's lifetime. */
public class InMemorySeenItemsTracker : SeenItemsTracker {

    private val seen = mutableMapOf<SourceId, MutableSet<MediaItemId>>()

    override fun newItems(source: SourceId, items: List<MediaItem>): List<MediaItem> {
        val known = seen[source] ?: run {
            seen[source] = items.mapTo(mutableSetOf()) { it.id }
            return emptyList()
        }
        return items.filter { it.id !in known }
    }

    override fun markSeen(source: SourceId, items: List<MediaItem>) {
        seen.getOrPut(source) { mutableSetOf() }.addAll(items.map { it.id })
    }
}
