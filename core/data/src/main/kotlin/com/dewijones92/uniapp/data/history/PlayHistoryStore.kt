package com.dewijones92.uniapp.data.history

import com.dewijones92.uniapp.data.playlist.PlaylistItem
import kotlinx.coroutines.flow.Flow

/**
 * Records recently-played items across both pillars, most-recent first. Reuses
 * [PlaylistItem] (a [com.dewijones92.uniapp.domain.MediaItem] + a stable play
 * handle) so a video keeps its watch URL and a podcast its enclosure — history
 * replays survive stream-URL expiry, and it plays back through the same seam.
 */
public interface PlayHistoryStore {

    /** Recently-played items, most-recent first (capped). */
    public fun observe(): Flow<List<PlaylistItem>>

    /** Records [item] as just-played (moving it to the front if already present). */
    public suspend fun record(item: PlaylistItem)

    public suspend fun clear()
}
