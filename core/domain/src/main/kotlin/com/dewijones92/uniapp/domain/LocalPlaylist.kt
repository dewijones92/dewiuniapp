package com.dewijones92.uniapp.domain

/** Stable identity of a user-created local playlist; never blank. */
@JvmInline
public value class PlaylistId(public val value: String) {
    init {
        require(value.isNotBlank()) { "PlaylistId must not be blank" }
    }
}

/**
 * A user-curated local playlist. Unified across both pillars — one playlist can
 * hold podcast episodes and videos together (a playlist that couldn't would be a
 * design failure). Distinct from the transient up-next queue and from remote
 * YouTube account playlists.
 */
public data class LocalPlaylist(
    public val id: PlaylistId,
    public val name: String,
    public val itemCount: Int = 0,
)
