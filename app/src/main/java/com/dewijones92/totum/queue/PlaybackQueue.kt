package com.dewijones92.totum.queue

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.data.queue.QueueEntry
import com.dewijones92.totum.data.queue.QueueGroup
import com.dewijones92.totum.data.queue.QueueSnapshot
import com.dewijones92.totum.data.queue.QueueSnapshot.Companion.NOTHING_PLAYING
import com.dewijones92.totum.data.queue.QueueStore
import com.dewijones92.totum.data.queue.fake.InMemoryQueueStore
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.playback.PlaybackController
import com.dewijones92.totum.video.VideoPlaybackLauncher
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
 * The app's single queue, unified across both pillars, and the spine of playback:
 * tapping anything anywhere lands here.
 *
 * The playing item is a **member** of the queue, addressed by a cursor, not something
 * living outside it. That is what makes jumping and advancing non-destructive —
 * moving the cursor leaves everything before it in place, so you can go back — and it
 * is what makes [peek] meaningful: playing something that never joins the queue.
 *
 * Entries are [PlayableItem]s, the same shape local playlists and play history store,
 * so "Play all" and "replay from history" need no conversion. Each carries an optional
 * [QueueGroup] tag naming the run it arrived in; the list itself stays flat, so
 * grouping costs playback nothing.
 *
 * Videos resolve just-in-time when they become current, so a queue of videos never
 * pre-extracts URLs that would expire. The whole thing is persisted through
 * [QueueStore] — cursor included — so it survives a restart.
 */
// The queue's whole command surface (add/insert/remove/reorder/jump/advance), each a
// small operation over one list plus a cursor. Splitting it would scatter the single
// owner of queue order, which is the point of the class.
@Suppress("TooManyFunctions")
class PlaybackQueue(
    private val controller: PlaybackController,
    private val launcher: VideoPlaybackLauncher,
    private val scope: CoroutineScope,
    private val store: QueueStore = InMemoryQueueStore(),
    /**
     * Called when the user deliberately queues a single item, so the choice can be mirrored
     * somewhere else — today, to YouTube's Watch Later, which is how queueing something here
     * becomes a preference signal on the account.
     *
     * A hook rather than a YouTube dependency: the queue has no business knowing an account
     * exists, and the mirror is wired in AppContainer where every other integration lives.
     *
     * Deliberately NOT called by playAll or playNow. playAll is a bulk run — the shorts reel
     * hands over fifty items at a time and would bury Watch Later — and playNow means "I am
     * watching this now", which the watch-history sync already reports. Watch Later is for
     * intent, so only the two add-paths that express intent fire it.
     */
    private val onQueuedByUser: suspend (PlayableItem) -> Unit = {},
) {
    private val _state = MutableStateFlow(QueueSnapshot())

    /** The queue and where playback is within it. */
    val state: StateFlow<QueueSnapshot> = _state.asStateFlow()

    private val _nowPlaying = MutableStateFlow<PlayableItem?>(null)

    /**
     * The item most recently handed to the player, whether or not it is a queue member.
     *
     * Distinct from `state.value.current` on purpose. The cursor answers "where are we in the
     * queue", which is -1 for a peek and for anything played before hydration lands — and the
     * player was using the cursor to decide whether to offer its item actions, so add-to-queue
     * and friends silently disappeared for exactly those items. "What is playing" and "where
     * is the cursor" are different questions and now have different answers.
     */
    val nowPlaying: StateFlow<PlayableItem?> = _nowPlaying.asStateFlow()

    /**
     * Whether anything has changed the queue yet. Loading is suspending, so the user
     * can act before it lands — this makes their intent win instead of being
     * silently replaced by the restored queue.
     */
    private var touched = false

    init {
        scope.launch {
            val saved = store.load().deduplicated()
            Diag.log(
                "queue",
                "hydrated ${saved.entries.size} entries, cursor ${saved.currentIndex}" +
                    if (touched) " — discarded, the user got there first" else "",
            )
            if (!touched) _state.value = saved
        }
        // Persist every subsequent change. `drop(1)` skips the initial empty value
        // so an empty start can't wipe a saved queue before hydration lands.
        _state.drop(1).onEach { store.save(it) }.launchIn(scope)
    }

    /** Adds to the end of the queue, moving it there if it is already queued. */
    fun enqueue(item: PlayableItem, group: QueueGroup? = null) {
        mutate("add-to-end") { snapshot ->
            snapshot.relocating(item) { without ->
                without.copy(entries = without.entries + QueueEntry(item, group))
            }
        }
        mirror(item)
    }

    /** Inserts so it plays immediately after the current entry, moving it if already queued. */
    fun playNext(item: PlayableItem, group: QueueGroup? = null) {
        mutate("play-next") { snapshot ->
            snapshot.relocating(item) { without ->
                without.inserted(listOf(QueueEntry(item, group)))
            }
        }
        mirror(item)
    }

    /**
     * Fires the mirror without letting it affect queueing.
     *
     * Its own coroutine and its own try/catch: the queue must change instantly and locally
     * whatever the network does, so a slow or failed Watch Later write can never delay a tap or
     * lose the queue entry that the user actually asked for.
     */
    private fun mirror(item: PlayableItem) {
        scope.launch {
            runCatching { onQueuedByUser(item) }
                .onFailure { Diag.warn("queue", "could not mirror \"${item.item.title}\" to the account", it) }
        }
    }

    /**
     * The app's normal "tap to play": puts [item] in the queue at the current
     * position and plays it, so pressing something never discards what was lined up.
     * An item already queued is moved rather than duplicated.
     */
    suspend fun playNow(item: PlayableItem, group: QueueGroup? = null): Boolean {
        var index = NOTHING_PLAYING
        mutate("play-now") { snapshot ->
            val withoutIt = snapshot.removing { it.item.item.id == item.item.id }
            withoutIt.inserted(listOf(QueueEntry(item, group))).also { index = it.currentIndex + 1 }
        }
        return playAt(index)
    }

    /**
     * Plays [items] as a run inserted after the current entry, starting with the
     * first. Deliberately does not replace the queue — an unwanted run is one
     * "remove these" away, whereas a replaced queue is gone. No-op if empty.
     */
    fun playAll(items: List<PlayableItem>, group: QueueGroup? = null) {
        if (items.isEmpty()) return
        // Distinct within the run as well as against the queue: a caller can legitimately hand
        // over a list with repeats (a feed showing the same video twice), and re-opening the
        // shorts reel hands over the whole run again every time.
        val run = items.distinctBy { it.item.id }
        val ids = run.map { it.item.id }.toSet()
        var index = NOTHING_PLAYING
        mutate("play-all(${run.size})") { snapshot ->
            snapshot
                .removing { entry -> entry.item.item.id in ids && entry != snapshot.current }
                .inserted(run.map { QueueEntry(it, group) })
                .also { index = it.currentIndex + 1 }
        }
        scope.launch { playAt(index) }
    }

    /**
     * Plays [item] **without it joining the queue** — "peek": a one-off listen or
     * watch that leaves the queue, and your place in it, exactly as they were. The
     * cursor is cleared, so the next advance restarts from the queue's beginning
     * rather than pretending the peeked item was a member.
     */
    suspend fun peek(item: PlayableItem): Boolean {
        mutate("peek (cursor cleared by design)") { it.copy(currentIndex = NOTHING_PLAYING) }
        return play(item)
    }

    /** Plays the entry at [index]; nothing before it is discarded. */
    fun jumpTo(index: Int) {
        scope.launch { playAt(index) }
    }

    fun removeAt(index: Int) {
        mutate("remove-at-$index") { snapshot ->
            if (index !in snapshot.entries.indices) {
                snapshot
            } else {
                snapshot.removingAt(index)
            }
        }
    }

    /** Drops every entry tagged with [groupId] — the batch action a grouped run offers. */
    fun removeGroup(groupId: String) {
        mutate("remove-group") { snapshot -> snapshot.removing { it.group?.id == groupId } }
    }

    /** Reorders one entry, carrying the cursor with the entry it points at. */
    fun move(from: Int, to: Int) {
        mutate("move $from->$to") { snapshot ->
            if (from !in snapshot.entries.indices || to !in snapshot.entries.indices) {
                snapshot
            } else {
                val current = snapshot.current
                val reordered = snapshot.entries.toMutableList().apply { add(to, removeAt(from)) }
                snapshot.copy(
                    entries = reordered,
                    currentIndex = current?.let(reordered::indexOf) ?: snapshot.currentIndex,
                )
            }
        }
    }

    fun clear() {
        mutate("clear") { QueueSnapshot() }
    }

    /**
     * Starts the entry after the one that is PLAYING, skipping any that fail to play (an expired
     * or private video, a broken item) so one bad entry cannot strand the rest.
     *
     * Advances from what is playing rather than from the stored cursor, and that distinction is
     * the whole bug this fixes. A peeked item plays with the cursor at -1 **by design**, so
     * `currentIndex + 1` was `0` — and when the peeked item was itself somewhere in the queue,
     * "advancing" replayed the very video that had just finished. A real report (0.1.199): peek at
     * 19:11, ended at 19:27, `advance=true`, and the next transition was the same id. It then
     * ended again and was refused as "already handled", leaving playback stuck until Dewi moved
     * it by hand.
     *
     * Suspending, so the returned value is the truth. It used to return `true` the moment an index
     * existed, before the coroutine had tried anything — which is why the trail said `advance=true`
     * while nothing had actually moved on, and why the report was harder to read than it should
     * have been.
     */
    suspend fun playNextInQueue(): Boolean {
        val snapshot = _state.value
        val playingId = _nowPlaying.value?.item?.id
        val from = playingId
            ?.let { id -> snapshot.entries.indexOfFirst { it.item.item.id == id } }
            ?.takeIf { it >= 0 }
            ?: snapshot.currentIndex
        var index = from + 1
        if (index > snapshot.entries.lastIndex) {
            Diag.log("queue", "nothing after ${playingId?.value ?: "cursor $from"} of ${snapshot.entries.size}")
            return false
        }
        while (index <= _state.value.entries.lastIndex) {
            val entry = _state.value.entries[index]
            // Never advance onto the thing already playing. Belt and braces alongside the index
            // fix above: it also covers a duplicate that predates the de-duplication work.
            if (entry.item.item.id != playingId && playAt(index)) return true
            index++
        }
        Diag.log("queue", "nothing playable after index $from")
        return false
    }

    /** Moves the cursor to [index] and plays it; false when out of range or unplayable. */
    private suspend fun playAt(index: Int): Boolean {
        val entry = _state.value.entries.getOrNull(index) ?: return false
        mutate("play-at-$index") { it.copy(currentIndex = index) }
        return play(entry.item)
    }

    /**
     * Every change goes through here, so nothing can bypass the hydration guard.
     *
     * [why] names the operation, because the snapshot alone is ambiguous in exactly the way
     * that matters: a cursor of -1 is what both a "peek" and a hydration-with-nothing-playing
     * look like, and telling them apart decided whether an auto-advance failure was a bug or
     * by design. One word of intent per mutation makes the trail readable.
     */
    private fun mutate(why: String, block: (QueueSnapshot) -> QueueSnapshot) {
        touched = true
        _state.update(block)
        val now = _state.value
        Diag.log(
            "queue",
            "$why: size=${now.entries.size} current=${now.currentIndex} ${now.current?.item?.item?.title ?: "-"}",
        )
    }

    /**
     * Replays whatever is current from [positionMs] — how an expired stream is recovered.
     *
     * Goes back through [play] rather than nudging the player, because for a video that
     * routing is what re-resolves the URL: the queue holds the stable watch URL, never the
     * signed one that died.
     */
    suspend fun replayCurrent(positionMs: Long): Boolean {
        val entry = _state.value.current ?: return false
        return play(entry.item, positionMs)
    }

    /** Plays [queued]; returns whether it actually started. */
    private suspend fun play(queued: PlayableItem, startPositionMs: Long = 0): Boolean {
        // Recorded before routing, so a peek and a queued play are equally "playing".
        _nowPlaying.value = queued
        return route(queued, startPositionMs)
    }

    private suspend fun route(queued: PlayableItem, startPositionMs: Long): Boolean =
        when (val handle = queued.handle) {
            is PlayHandle.Video -> launcher.play(handle.watchUrl, queued.item.sourceId, startPositionMs)
            is PlayHandle.LocalVideo -> {
                launcher.playLocal(queued.item, handle.localPath)
                true
            }
            is PlayHandle.Podcast -> {
                // A podcast needs either a downloaded file or a stream URL; skip if neither.
                if (handle.localPath == null && queued.item.mediaUrl == null) {
                    false
                } else {
                    controller.play(
                        queued.item,
                        MediaKind.PODCAST,
                        localPath = handle.localPath,
                        startPositionMs = startPositionMs,
                    )
                    true
                }
            }
        }
}

/**
 * Drops repeats, keeping the first of each and carrying the cursor with the entry it points at.
 *
 * Applied on load, so a queue already polluted by the duplicating add-paths repairs itself on
 * next launch rather than staying broken forever. It is also what makes the list safe to key by
 * item id alone: duplicate keys in a LazyColumn are a crash, and the old key had to include the
 * index to stay unique — which defeated Compose's item identity, so reordering lost per-item
 * state and never animated.
 */
private fun QueueSnapshot.deduplicated(): QueueSnapshot {
    val unique = entries.distinctBy { it.item.item.id }
    if (unique.size == entries.size) return this
    Diag.warn("queue", "dropped ${entries.size - unique.size} duplicate entries on load")
    return copy(entries = unique, currentIndex = current?.let(unique::indexOf) ?: NOTHING_PLAYING)
}

/**
 * Applies [place] to this snapshot with any existing copy of [item] removed, so re-adding
 * something MOVES it instead of duplicating it.
 *
 * Dewi hit this on "play next": pressing it on an item already in the queue left two copies.
 * playNow already de-duplicated and its documentation even claimed the behaviour was general,
 * so three of the four add-paths disagreed with the one that was right.
 *
 * The playing entry is deliberately exempt. Removing it would drop the cursor to -1 and the
 * queue would forget where it was, so "play next" on the thing already playing does nothing —
 * which is also the only sensible reading of the request.
 */
private fun QueueSnapshot.relocating(
    item: PlayableItem,
    place: (QueueSnapshot) -> QueueSnapshot,
): QueueSnapshot {
    if (current?.item?.item?.id == item.item.id) return this
    return place(removing { it.item.item.id == item.item.id })
}

/** Inserts [run] immediately after the current entry, leaving the cursor put. */
private fun QueueSnapshot.inserted(run: List<QueueEntry>): QueueSnapshot {
    val at = (currentIndex + 1).coerceIn(0, entries.size)
    return copy(entries = entries.take(at) + run + entries.drop(at))
}

/** Drops matching entries, keeping the cursor on whatever it pointed at. */
private fun QueueSnapshot.removing(match: (QueueEntry) -> Boolean): QueueSnapshot {
    val kept = entries.filterNot(match)
    return copy(entries = kept, currentIndex = current?.let(kept::indexOf) ?: NOTHING_PLAYING)
}

private fun QueueSnapshot.removingAt(index: Int): QueueSnapshot {
    val kept = entries.filterIndexed { i, _ -> i != index }
    // Removing the playing entry leaves nothing current; the next advance starts from
    // where it was, which is what "remove the thing I'm on" should feel like.
    val cursor = when {
        index == currentIndex -> (currentIndex - 1).coerceAtLeast(NOTHING_PLAYING)
        index < currentIndex -> currentIndex - 1
        else -> currentIndex
    }
    return copy(entries = kept, currentIndex = cursor)
}
