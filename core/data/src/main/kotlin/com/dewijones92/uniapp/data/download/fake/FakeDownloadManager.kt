package com.dewijones92.uniapp.data.download.fake

import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [DownloadManager] for tests and previews; downloads complete instantly. */
public class FakeDownloadManager : DownloadManager {

    private val downloads = MutableStateFlow<Map<MediaItemId, DownloadState>>(emptyMap())

    override fun observeDownloads(): Flow<Map<MediaItemId, DownloadState>> = downloads

    override fun observe(id: MediaItemId): Flow<DownloadState> =
        downloads.map { it[id] ?: DownloadState.NotDownloaded }

    override suspend fun download(item: MediaItem) {
        downloads.update { it + (item.id to DownloadState.Downloaded("/fake/${item.id.value}.media")) }
    }

    override suspend fun delete(id: MediaItemId) {
        downloads.update { it - id }
    }
}
