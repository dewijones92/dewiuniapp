package com.dewijones92.uniapp.queue

import com.dewijones92.uniapp.data.queue.QueueEntry
import com.dewijones92.uniapp.data.queue.QueueGroup
import com.dewijones92.uniapp.data.queue.QueueStore
import com.dewijones92.uniapp.data.queue.fake.InMemoryQueueStore
import com.dewijones92.uniapp.domain.MediaKind
import com.dewijones92.uniapp.domain.PlayHandle
import com.dewijones92.uniapp.domain.PlayableItem
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The app's single up-next queue, unified across both pillars. Holds the items
 * to play AFTER the current one; the currently-playing item lives in the
 * playback controller's state. Video items resolve just-in-time when they reach
 * the front, so a queue of videos never pre-extracts expiring URLs.
 *
 * Entries are [PlayableItem]s — the same shape local playlists and play history
 * store, so "Play all" and "replay from history" need no conversion. Each carries
 * an optional [QueueGroup] tag naming the run it arrived in; the queue itself
 * stays **flat**, so grouping costs playback nothing.
 *
 * The queue is persisted through [QueueStore]: hydrated once at construction and
 * saved on every change, so it survives a restart.
 */
// The queue's whole command surface (add/insert/remove/reorder/jump/advance), each a
// small operation over one list. Splitting it would scatter the single owner of
// queue order, which is the point of the class.
@Suppress("TooManyFunctions")
class PlaybackQueue(
    private val controller: PlaybackController,
    private val launcher: VideoPlaybackLauncher,
    private val scope: CoroutineScope,
    private val store: QueueStore = InMemoryQueueStore(),
) {
    private val _upNext = MutableStateFlow<List<QueueEntry>>(emptyList())
    val upNext: StateFlow<List<QueueEntry>> = _upNext.asStateFlow()

    /**
     * Whether anything has changed the queue yet. Loading is suspending, so the user
     * can act before it lands — this makes their intent win instead of being
     * silently replaced by the restored queue.
     */
    private var touched = false

    init {
        scope.launch {
            val saved = store.load()
            if (!touched) _upNext.value = saved
        }
        // Persist every subsequent change. `drop(1)` skips the initial empty value
        // so an empty start can't wipe a saved queue before hydration lands.
        _upNext.drop(1).onEach { store.save(it) }.launchIn(scope)
    }

    /** Adds to the end of the queue. */
    fun enqueue(item: PlayableItem, group: QueueGroup? = null) {
        mutate { it + QueueEntry(item, group) }
    }

    /** Plays [items] as a set: the first now, the rest queued up next. No-op if empty. */
    fun playAll(items: List<PlayableItem>, group: QueueGroup? = null) {
        if (items.isEmpty()) return
        mutate { items.drop(1).map { item -> QueueEntry(item, group) } }
        scope.launch { play(items.first()) }
    }

    /** Inserts so it plays immediately after the current item. */
    fun playNext(item: PlayableItem, group: QueueGroup? = null) {
        mutate { listOf(QueueEntry(item, group)) + it }
    }

    fun removeAt(index: Int) {
        mutate { list -> list.filterIndexed { i, _ -> i != index } }
    }

    /** Drops every entry tagged with [groupId] — the batch action a grouped run offers. */
    fun removeGroup(groupId: String) {
        mutate { list -> list.filterNot { it.group?.id == groupId } }
    }

    /** Reorders one entry; a no-op if either index is out of range. */
    fun move(from: Int, to: Int) {
        mutate { list ->
            if (from !in list.indices || to !in list.indices) {
                list
            } else {
                list.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
    }

    fun clear() {
        mutate { emptyList() }
    }

    /** Plays the entry at [index] now, dropping it and everything before it. No-op if out of range. */
    fun playFromQueue(index: Int) {
        val list = _upNext.value
        val target = list.getOrNull(index) ?: return
        mutate { it.drop(index + 1) }
        scope.launch { play(target.item) }
    }

    /** Every change goes through here, so nothing can bypass the hydration guard. */
    private fun mutate(block: (List<QueueEntry>) -> List<QueueEntry>) {
        touched = true
        _upNext.update(block)
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
                val head = getAndTake() ?: break
                played = play(head.item)
            }
        }
        return true
    }

    /** Pops and returns the head, or null if empty. */
    private fun getAndTake(): QueueEntry? {
        var head: QueueEntry? = null
        mutate { list ->
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
