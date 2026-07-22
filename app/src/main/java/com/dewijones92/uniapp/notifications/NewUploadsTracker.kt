package com.dewijones92.uniapp.notifications

import android.content.Context
import androidx.core.content.edit
import com.dewijones92.uniapp.innertube.feeds.FeedVideo

/**
 * Works out which subscription-feed videos are "new" since the user last looked,
 * standing in for a real notifications bell (YouTube's notification endpoint
 * isn't reachable with this app's TV-OAuth token). Backed by a set of
 * already-seen video ids.
 *
 * First run bootstraps: everything currently in the feed is treated as already
 * seen, so only genuinely-new uploads show up afterwards — no launch-day flood.
 */
interface NewUploadsTracker {
    /** The feed videos the user hasn't seen yet (newest-first order preserved). */
    fun newUploads(feed: List<FeedVideo>): List<FeedVideo>

    /** Marks these videos seen, so they stop counting as new. */
    fun markSeen(feed: List<FeedVideo>)
}

/** SharedPreferences-backed tracker; the seen set persists across launches. */
class SharedPrefsNewUploadsTracker(context: Context) : NewUploadsTracker {

    private val prefs = context.getSharedPreferences("uniapp_notifications", Context.MODE_PRIVATE)

    override fun newUploads(feed: List<FeedVideo>): List<FeedVideo> {
        val seen = prefs.getStringSet(KEY_SEEN, null)
        if (seen == null) {
            saveSeen(feed.map { it.videoId }.toSet())
            return emptyList()
        }
        return feed.filter { it.videoId !in seen }
    }

    override fun markSeen(feed: List<FeedVideo>) {
        val seen = (prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()).toMutableSet()
        seen += feed.map { it.videoId }
        // Keep the seen set bounded; the current feed ids are the ones that matter.
        saveSeen(if (seen.size > MAX_SEEN) feed.map { it.videoId }.toSet() else seen)
    }

    private fun saveSeen(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_SEEN, ids) }
    }

    private companion object {
        const val KEY_SEEN = "seen_video_ids"
        const val MAX_SEEN = 2000
    }
}

/** In-memory tracker for previews and tests. */
class InMemoryNewUploadsTracker : NewUploadsTracker {
    private var seen: Set<String>? = null

    override fun newUploads(feed: List<FeedVideo>): List<FeedVideo> {
        val current = seen ?: run {
            seen = feed.map { it.videoId }.toSet()
            return emptyList()
        }
        return feed.filter { it.videoId !in current }
    }

    override fun markSeen(feed: List<FeedVideo>) {
        seen = (seen ?: emptySet()) + feed.map { it.videoId }
    }
}
