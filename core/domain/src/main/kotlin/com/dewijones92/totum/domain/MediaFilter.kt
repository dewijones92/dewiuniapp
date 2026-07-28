package com.dewijones92.totum.domain

/**
 * Which items a list should show, by how far through them you are.
 *
 * The last real parity gap against AntennaPod: a feed that keeps showing what you have already
 * finished gets steadily less useful the more you use it, and with a sixty-item queue that is
 * every day rather than occasionally.
 *
 * Deliberately about [PlayState] and nothing else. A "downloaded only" filter is a different
 * question — it asks about storage, not progress — and folding both into one enum would make
 * the combinations meaningless (what is "unplayed or downloaded"?). If that lands later it
 * belongs beside this, not inside it.
 */
public enum class MediaFilter {
    /** Everything, in the source's own order. */
    ALL,

    /** Hides finished items, keeping anything started-but-unfinished. */
    UNPLAYED,

    /** Only items you are part-way through — the "carry on where I left off" view. */
    IN_PROGRESS,
    ;

    public fun accepts(state: PlayState): Boolean = when (this) {
        ALL -> true
        UNPLAYED -> state !is PlayState.Played
        IN_PROGRESS -> state is PlayState.InProgress
    }
}

/**
 * Applies [filter] using [stateOf] to look each item's progress up.
 *
 * Takes a lookup rather than a map so the caller decides where progress comes from, and so this
 * stays in the domain module with no opinion about storage. An item with no recorded progress is
 * [PlayState.Unplayed], which is what makes ALL and UNPLAYED identical on a fresh install —
 * correct, and the reason the chip row does not appear until it would do something.
 */
public fun List<MediaItem>.filteredBy(
    filter: MediaFilter,
    stateOf: (MediaItemId) -> PlayState,
): List<MediaItem> = when (filter) {
    MediaFilter.ALL -> this
    else -> filter { filter.accepts(stateOf(it.id)) }
}
