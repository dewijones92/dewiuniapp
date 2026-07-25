package com.dewijones92.uniapp.queue

import com.dewijones92.uniapp.domain.MediaKind
import com.dewijones92.uniapp.domain.PlayHandle
import com.dewijones92.uniapp.domain.PlayableItem
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The app's single up-next queue, unified across both pillars. Holds the items
 * to play AFTER the current one; the currently-playing item lives in the
 * playback controller's state. Tapping to play something goes through the normal
 * paths — this only governs what plays next (auto-advance at end, skip-to-next,
 * and the up-next list). Video items resolve just-in-time when they reach the
 * front, so a queue of videos never pre-extracts expiring URLs.
 *
 * Entries are [PlayableItem]s — the same shape local playlists and play history
 * store, so "Play all" and "replay from history" need no conversion.
 */
class PlaybackQueue(
    private val controller: PlaybackController,
    private val launcher: VideoPlaybackLauncher,
    private val scope: CoroutineScope,
) {
    private val _upNext = MutableStateFlow<List<PlayableItem>>(emptyList())
    val upNext: StateFlow<List<PlayableItem>> = _upNext.asStateFlow()

    /** Adds to the end of the queue. */
    fun enqueue(queued: PlayableItem) {
        _upNext.update { it + queued }
    }

    /** Plays [items] as a set: the first now, the rest queued up next. No-op if empty. */
    fun playAll(items: List<PlayableItem>) {
        if (items.isEmpty()) return
        _upNext.value = items.drop(1)
        scope.launch { play(items.first()) }
    }

    /** Inserts so it plays immediately after the current item. */
    fun playNext(queued: PlayableItem) {
        _upNext.update { listOf(queued) + it }
    }

    fun removeAt(index: Int) {
        _upNext.update { list -> list.filterIndexed { i, _ -> i != index } }
    }

    /** Reorders one entry; a no-op if either index is out of range. */
    fun move(from: Int, to: Int) {
        _upNext.update { list ->
            if (from !in list.indices || to !in list.indices) {
                list
            } else {
                list.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
    }

    fun clear() {
        _upNext.value = emptyList()
    }

    /** Plays the entry at [index] now, dropping it and everything before it. No-op if out of range. */
    fun playFromQueue(index: Int) {
        val list = _upNext.value
        val target = list.getOrNull(index) ?: return
        _upNext.value = list.drop(index + 1)
        scope.launch { play(target) }
    }

    /**
     * Starts the next queued item, skipping any that fail to play (an expired or
     * private video, a broken item) so one bad entry can't strand the rest of the
     * queue. Returns whether there was anything to try.
     */
    fun playNextInQueue(): Boolean {
        if (_upNext.value.isEmpty()) return false
        scope.launch {
            var played = false
            while (!played) {
                val head = _upNext.getAndTake() ?: break
                played = play(head)
            }
        }
        return true
    }

    /** Pops and returns the head, or null if empty. */
    private fun MutableStateFlow<List<PlayableItem>>.getAndTake(): PlayableItem? {
        var head: PlayableItem? = null
        update { list ->
            head = list.firstOrNull()
            if (list.isEmpty()) list else list.drop(1)
        }
        return head
    }

    /** Plays [queued]; returns whether it actually started. */
    private suspend fun play(queued: PlayableItem): Boolean = when (val handle = queued.handle) {
        is PlayHandle.Video -> launcher.play(handle.watchUrl, queued.item.sourceId)
        is PlayHandle.LocalVideo -> {
            launcher.playLocal(queued.item, handle.localPath)
            true
        }
        is PlayHandle.Podcast -> {
            // A podcast needs either a downloaded file or a stream URL; skip if neither.
            if (handle.localPath == null && queued.item.mediaUrl == null) {
                false
            } else {
                controller.play(queued.item, MediaKind.PODCAST, localPath = handle.localPath)
                true
            }
        }
    }
}
