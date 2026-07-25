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
)
