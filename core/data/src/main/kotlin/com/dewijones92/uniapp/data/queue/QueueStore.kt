package com.dewijones92.uniapp.data.queue

import com.dewijones92.uniapp.domain.PlayableItem

/**
 * Where a queue entry came from — a local playlist, a podcast feed, a channel, or
 * an ad-hoc "Play all". Purely a **display** tag: playback never looks at it.
 */
public data class QueueGroup(public val id: String, public val title: String)

/**
 * One entry in the up-next queue: what to play, plus the group it arrived with.
 *
 * The queue stays a **flat** list for playback — grouping is rendered by drawing a
 * header over each contiguous run of the same [group]. That means "what does next
 * mean at a group boundary?" never arises, dragging an item out of a run simply
 * splits it, and there is no invariant to repair.
 */
public data class QueueEntry(
    public val item: PlayableItem,
    public val group: QueueGroup? = null,
)

/**
 * Persists the up-next queue so it survives a restart — necessary once the queue
 * is the spine of playback with its own tab, rather than a transient side-car.
 *
 * Deliberately load/save rather than an observable: the in-memory queue is the
 * authority while the app runs, and an observable store would feed its own writes
 * back in.
 */
public interface QueueStore {

    /** The saved queue, in order; empty when nothing was saved. */
    public suspend fun load(): List<QueueEntry>

    /** Replaces the saved queue with [entries]. */
    public suspend fun save(entries: List<QueueEntry>)
}
