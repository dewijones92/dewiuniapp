package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItemId
import kotlinx.coroutines.flow.Flow

/** Persistence port for download records; implemented by :core:database (Room). */
public interface DownloadStore {
    public fun observeAll(): Flow<Map<MediaItemId, DownloadState>>
    public suspend fun put(id: MediaItemId, state: DownloadState)
    public suspend fun get(id: MediaItemId): DownloadState
    public suspend fun remove(id: MediaItemId)
}
