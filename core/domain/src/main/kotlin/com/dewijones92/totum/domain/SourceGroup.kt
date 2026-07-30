package com.dewijones92.totum.domain

/** Stable identity of a [SourceGroup]; never blank. */
@JvmInline
public value class SourceGroupId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SourceGroupId must not be blank" }
    }
}

/**
 * A named set of sources whose content is read as one feed — Dewi's ask: "special groups
 * that are special channels… channel a channel b channel c… I want the live stream, short,
 * everything".
 *
 * Deliberately a group of [SourceId], not of YouTube channels. A podcast feed and a video
 * channel are both sources, so a group can hold either or both and the merged feed simply
 * merges what it finds — the pillar-agnostic seam the project's first law asks for, at no
 * extra cost. Nothing here knows what a channel is.
 *
 * [sourceIds] keeps the user's order: it is how the group is listed and edited. The merged
 * feed sorts by date instead, because "newest across these channels" is the point of the
 * feature — the order here is for editing, not for reading.
 */
public data class SourceGroup(
    val id: SourceGroupId,
    val name: String,
    val sourceIds: List<SourceId> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "a group must be named" }
    }

    /** Distinct membership, first-seen order — the same source twice is one membership. */
    public val members: List<SourceId> get() = sourceIds.distinct()

    public operator fun contains(sourceId: SourceId): Boolean = sourceId in sourceIds

    /** Adds [sourceId] if absent, else returns this unchanged — toggling is the only edit. */
    public fun with(sourceId: SourceId): SourceGroup =
        if (sourceId in this) this else copy(sourceIds = sourceIds + sourceId)

    public fun without(sourceId: SourceId): SourceGroup =
        copy(sourceIds = sourceIds.filterNot { it == sourceId })
}
