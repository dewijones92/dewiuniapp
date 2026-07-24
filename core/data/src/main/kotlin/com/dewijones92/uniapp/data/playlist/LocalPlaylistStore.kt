package com.dewijones92.uniapp.data.playlist

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.LocalPlaylist
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.PlaylistId
import kotlinx.coroutines.flow.Flow

/**
 * How a saved playlist item is played — the same shapes the up-next queue uses,
 * so "Play all" maps straight onto the queue. A video keeps its stable watch URL
 * (streams expire, so it re-resolves on play); podcasts and downloads are ready.
 */
public sealed interface PlaylistPlayback {
    public data class Video(public val watchUrl: HttpUrl) : PlaylistPlayback
    public data class LocalVideo(public val localPath: String) : PlaylistPlayback
    public data class Podcast(public val localPath: String? = null) : PlaylistPlayback
}

/** One entry in a local playlist: what to show ([item]) and how to play it. */
public data class PlaylistItem(
    public val item: MediaItem,
    public val playback: PlaylistPlayback,
)

/**
 * Stores user-curated local playlists (both pillars). One seam; the Room-backed
 * implementation denormalizes each item so a playlist survives offline and stream
 * expiry.
 */
public interface LocalPlaylistStore {

    /** All playlists, most-recently-created first, with their item counts. */
    public fun observePlaylists(): Flow<List<LocalPlaylist>>

    /** The items of one playlist, in order. */
    public fun observeItems(id: PlaylistId): Flow<List<PlaylistItem>>

    /** Creates an empty playlist, returning its id. */
    public suspend fun create(name: String): PlaylistId

    public suspend fun rename(id: PlaylistId, name: String)

    public suspend fun delete(id: PlaylistId)

    /** Appends [item] to the end of the playlist (idempotent per item id). */
    public suspend fun addItem(id: PlaylistId, item: PlaylistItem)

    public suspend fun removeItem(id: PlaylistId, itemId: com.dewijones92.uniapp.domain.MediaItemId)
}
