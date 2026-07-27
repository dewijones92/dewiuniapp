package com.dewijones92.totum.data.download

import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.DownloadedMedia
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayableItem
import kotlinx.coroutines.flow.Flow

/** Persistence port for download records; implemented by :core:database (Room). */
public interface DownloadStore {
    public fun observeAll(): Flow<Map<MediaItemId, DownloadState>>

    /**
     * Every finished download with the item it belongs to — what an offline library
     * lists. A record stores the item itself, so this needs no pillar's catalogue to
     * join against.
     */
    public fun observeDownloaded(): Flow<List<DownloadedMedia>>

    /** Records [state] against [item], keeping the item's own columns current. */
    public suspend fun put(item: PlayableItem, state: DownloadState)
    public suspend fun get(id: MediaItemId): DownloadState
    public suspend fun remove(id: MediaItemId)
}
