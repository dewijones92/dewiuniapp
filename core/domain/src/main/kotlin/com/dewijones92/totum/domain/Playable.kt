package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl

/**
 * How to start playing something. A video keeps its **watch URL** rather than a
 * stream URL, because streaming URLs expire — it is re-resolved at play time.
 * Anything already on disk carries its path.
 */
public sealed interface PlayHandle {
    public data class Video(public val watchUrl: HttpUrl) : PlayHandle
    public data class LocalVideo(public val localPath: String) : PlayHandle
    public data class Podcast(public val localPath: String? = null) : PlayHandle

    /**
     * Which pillar this came from. Mixed lists (queue, history, playlists) label their
     * rows from this rather than sniffing a URL — the handle already knows, exactly.
     */
    public val pillar: MediaKind
        get() = when (this) {
            is Video, is LocalVideo -> MediaKind.VIDEO
            is Podcast -> MediaKind.PODCAST
        }
}

/**
 * A [MediaItem] plus how to play it — the one shape used wherever the app stores
 * or queues something playable: the up-next queue, local playlists, and play
 * history. Pillar-agnostic: which variant the [handle] is decides how playback
 * starts, and nothing above this needs to know.
 */
public data class PlayableItem(
    public val item: MediaItem,
    public val handle: PlayHandle,
) {
    /**
     * Where a download's bytes come from, or null if there is nothing to fetch yet.
     *
     * A video's stable **watch** URL wins over [MediaItem.mediaUrl]: that field holds a
     * resolved stream, which expires, and is absent entirely for anything queued from
     * search. The engine re-resolves from the watch URL, so this is the only reliable
     * answer — and having it in one place is what stops callers inventing their own.
     */
    public val fetchUrl: HttpUrl?
        get() = (handle as? PlayHandle.Video)?.watchUrl ?: item.mediaUrl
}

/**
 * Which pillar a raw feed [MediaItem] belongs to, inferred from its media URL. Only for
 * items that do **not** yet have a [PlayHandle] — anything holding one reads
 * [PlayHandle.pillar] instead, which knows exactly rather than guessing.
 *
 * This is the single place the guess lives. It used to live in two, with rules that
 * quietly disagreed: the playable mapping matched only `youtube.com/watch`, while the
 * download router matched any YouTube host. A Shorts URL therefore downloaded through
 * the video engine but was queued as if it were a podcast enclosure.
 */
public val MediaItem.pillar: MediaKind
    get() {
        val url = mediaUrl?.value ?: return MediaKind.PODCAST
        return if (STREAMING_HOSTS.any { it in url }) MediaKind.VIDEO else MediaKind.PODCAST
    }

private val STREAMING_HOSTS = listOf("youtube.com", "youtu.be")

/**
 * A feed item as something playable/saveable — a video keeps its watch URL as the
 * handle, a podcast its enclosure. Null when the item has no media URL yet, so there is
 * nothing to play.
 */
public fun MediaItem.toPlayableOrNull(): PlayableItem? {
    val url = mediaUrl ?: return null
    return PlayableItem(this, if (pillar == MediaKind.VIDEO) PlayHandle.Video(url) else PlayHandle.Podcast())
}

/**
 * As [toPlayableOrNull] but total. Downloads use this: an item with no URL still has to
 * become a row so the failure is recorded against something that names it, rather than
 * being dropped silently.
 */
public fun MediaItem.asPlayable(): PlayableItem = toPlayableOrNull() ?: PlayableItem(this, PlayHandle.Podcast())

/**
 * How a [PlayHandle] is written down, and read back.
 *
 * Here rather than in the database module because four tables and the backup file all
 * store the same two columns, and the vocabulary ("VIDEO", "PODCAST", "LOCAL_VIDEO") has
 * to mean the same thing in every one of them. A second copy would drift silently: a
 * backup written with one spelling and read with another restores a queue that plays
 * nothing.
 */
public fun PlayHandle.persisted(): Pair<String, String?> = when (this) {
    is PlayHandle.Video -> PERSISTED_VIDEO to watchUrl.value
    is PlayHandle.LocalVideo -> PERSISTED_LOCAL_VIDEO to localPath
    is PlayHandle.Podcast -> PERSISTED_PODCAST to localPath
}

/** The inverse of [persisted]; null when the stored pair cannot make a usable handle. */
public fun playHandleFrom(type: String, handle: String?): PlayHandle? = when (type) {
    PERSISTED_VIDEO -> handle?.let(HttpUrl::parse)?.let(PlayHandle::Video)
    PERSISTED_LOCAL_VIDEO -> handle?.let(PlayHandle::LocalVideo)
    else -> PlayHandle.Podcast(localPath = handle)
}

private const val PERSISTED_VIDEO = "VIDEO"
private const val PERSISTED_LOCAL_VIDEO = "LOCAL_VIDEO"
private const val PERSISTED_PODCAST = "PODCAST"
