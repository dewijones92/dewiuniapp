package com.dewijones92.uniapp.notifications

import android.content.Context
import androidx.core.content.edit
import com.dewijones92.uniapp.data.content.SeenItemsTracker
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId

/**
 * [SeenItemsTracker] backed by [android.content.SharedPreferences], one seen-set
 * of item ids per source. The [namespace] scopes the storage so independent
 * "unread" states (the in-app bell vs background notifications) never clobber
 * each other while sharing this one mechanism.
 *
 * A source's first query bootstraps it — its current items are recorded as seen
 * and nothing is reported — so neither first launch nor a freshly-added
 * subscription floods the user with "new" items.
 */
public class SharedPrefsSeenItemsTracker(
    context: Context,
    namespace: String,
) : SeenItemsTracker {

    private val prefs = context.getSharedPreferences("uniapp_seen_$namespace", Context.MODE_PRIVATE)

    override fun newItems(source: SourceId, items: List<MediaItem>): List<MediaItem> {
        val seen = prefs.getStringSet(source.key(), null)
        if (seen == null) {
            save(source, items.ids())
            return emptyList()
        }
        return items.filter { it.id.value !in seen }
    }

    override fun markSeen(source: SourceId, items: List<MediaItem>) {
        val current = prefs.getStringSet(source.key(), emptySet()).orEmpty()
        val updated = current + items.ids()
        // Keep the per-source set bounded; the ids currently present are the ones that matter.
        save(source, if (updated.size > MAX_SEEN_PER_SOURCE) items.ids() else updated)
    }

    private fun save(source: SourceId, ids: Set<String>) {
        prefs.edit { putStringSet(source.key(), ids) }
    }

    private fun SourceId.key(): String = "seen_$value"

    private fun List<MediaItem>.ids(): Set<String> = mapTo(mutableSetOf()) { it.id.value }

    private companion object {
        const val MAX_SEEN_PER_SOURCE = 500
    }
}
