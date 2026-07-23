package com.dewijones92.uniapp.queue

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaKind
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One item waiting in the up-next queue, carrying how to start it. Videos keep
 * their watch URL (resolved lazily when they reach the front, since stream URLs
 * expire); podcasts and downloads are ready to play. [item] is for display.
 */
public sealed interface QueuedItem {
    public val item: MediaItem

    public data class Video(override val item: MediaItem, val watchUrl: HttpUrl) : QueuedItem
    public data class LocalVideo(override val item: MediaItem, val localPath: String) : QueuedItem
    public data class Podcast(override val item: MediaItem, val localPath: String? = null) : QueuedItem
}

/**
 * The app's single up-next queue, unified across both pillars. Holds the items
 * to play AFTER the current one; the currently-playing item lives in the
 * playback controller's state. Tapping to play something goes through the normal
 * paths — this only governs what plays next (auto-advance at end, skip-to-next,
 * and the up-next list). Video items resolve just-in-time when they reach the
 * front, so a queue of videos never pre-extracts expiring URLs.
 */
class PlaybackQueue(
    private val controller: PlaybackController,
    private val launcher: VideoPlaybackLauncher,
    private val scope: CoroutineScope,
) {
    private val _upNext = MutableStateFlow<List<QueuedItem>>(emptyList())
    val upNext: StateFlow<List<QueuedItem>> = _upNext.asStateFlow()

    /** Adds to the end of the queue. */
    fun enqueue(queued: QueuedItem) {
        _upNext.update { it + queued }
    }

    /** Inserts so it plays immediately after the current item. */
    fun playNext(queued: QueuedItem) {
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
    private fun MutableStateFlow<List<QueuedItem>>.getAndTake(): QueuedItem? {
        var head: QueuedItem? = null
        update { list ->
            head = list.firstOrNull()
            if (list.isEmpty()) list else list.drop(1)
        }
        return head
    }

    /** Plays [queued]; returns whether it actually started. */
    private suspend fun play(queued: QueuedItem): Boolean = when (queued) {
        is QueuedItem.Video -> launcher.play(queued.watchUrl, queued.item.sourceId)
        is QueuedItem.LocalVideo -> {
            launcher.playLocal(queued.item, queued.localPath)
            true
        }
        is QueuedItem.Podcast -> {
            // A podcast needs either a downloaded file or a stream URL; skip if neither.
            if (queued.localPath == null && queued.item.mediaUrl == null) {
                false
            } else {
                controller.play(queued.item, MediaKind.PODCAST, localPath = queued.localPath)
                true
            }
        }
    }
}
