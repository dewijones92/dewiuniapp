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
 * Deliberately a group of [MediaSource], not of YouTube channels. A podcast feed and a video
 * channel are both sources, so a group can hold either or both and the merged feed simply
 * merges what it finds — the pillar-agnostic seam the project's first law asks for, at no
 * extra cost. Nothing here knows what a channel is.
 *
 * Members are whole [MediaSource]s, not ids. A group is allowed to hold a channel you have
 * not subscribed to — the picker is on every channel page — and resolving an id against the
 * app's subscriptions therefore found nothing for exactly those members, so the feed came
 * back empty. Proven on a device: "skipping …UCsBjURrPoezykLs9EqgamOA: no longer a known
 * source". A group carries what it needs to be read.
 *
 * [sources] keeps the user's order: it is how the group is listed and edited. The merged
 * feed sorts by date instead, because "newest across these channels" is the point of the
 * feature — the order here is for editing, not for reading.
 */
public data class SourceGroup(
    val id: SourceGroupId,
    val name: String,
    val sources: List<MediaSource> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "a group must be named" }
    }

    /** Distinct membership, first-seen order — the same source twice is one membership. */
    public val members: List<MediaSource> get() = sources.distinctBy { it.id }

    public operator fun contains(sourceId: SourceId): Boolean = sources.any { it.id == sourceId }

    /** Adds [source] if absent, else returns this unchanged — toggling is the only edit. */
    public fun with(source: MediaSource): SourceGroup =
        if (source.id in this) this else copy(sources = sources + source)

    public fun without(sourceId: SourceId): SourceGroup =
        copy(sources = sources.filterNot { it.id == sourceId })
}
