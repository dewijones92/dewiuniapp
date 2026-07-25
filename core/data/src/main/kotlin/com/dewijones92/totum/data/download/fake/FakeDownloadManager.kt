package com.dewijones92.totum.data.download.fake

import com.dewijones92.totum.data.download.DownloadEvent
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [DownloadManager] for tests and previews; downloads complete instantly. */
public class FakeDownloadManager : DownloadManager {

    private val downloads = MutableStateFlow<Map<MediaItemId, DownloadState>>(emptyMap())

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)

    override fun events(): Flow<DownloadEvent> = _events

    /** Emits an arbitrary transition, so a test can drive a consumer of [events]. */
    public fun emit(item: MediaItem, state: DownloadState) {
        _events.tryEmit(DownloadEvent(item, state))
    }

    override fun observeDownloads(): Flow<Map<MediaItemId, DownloadState>> = downloads

    override fun observe(id: MediaItemId): Flow<DownloadState> =
        downloads.map { it[id] ?: DownloadState.NotDownloaded }

    override suspend fun download(item: MediaItem, audioOnly: Boolean) {
        requested.add(item.id to audioOnly)
        downloads.update {
            it + (item.id to DownloadState.Downloaded("/fake/${item.id.value}.media", audioOnly = audioOnly))
        }
        _events.tryEmit(DownloadEvent(item, DownloadState.Downloaded("/fake/${item.id.value}.media", audioOnly)))
    }

    /** Every item asked for, in order, with the variant requested — for assertions. */
    public val requested: MutableList<Pair<MediaItemId, Boolean>> = mutableListOf()

    override suspend fun delete(id: MediaItemId) {
        downloads.update { it - id }
    }
}
