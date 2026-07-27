package com.dewijones92.totum.data.download.fake

import com.dewijones92.totum.data.download.DownloadEvent
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.DownloadedMedia
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayableItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [DownloadManager] for tests and previews; downloads complete instantly. */
public class FakeDownloadManager : DownloadManager {

    private val downloads = MutableStateFlow<Map<MediaItemId, DownloadState>>(emptyMap())

    private val completed = MutableStateFlow<List<DownloadedMedia>>(emptyList())

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)

    override fun events(): Flow<DownloadEvent> = _events

    /**
     * Parks a download mid-flight. [download] completes instantly, which is what most
     * tests want, but anything that observes work *in progress* needs a download that
     * stays in progress.
     */
    public fun setDownloading(id: MediaItemId, downloadedBytes: Long, totalBytes: Long?) {
        downloads.update { it + (id to DownloadState.Downloading(downloadedBytes, totalBytes)) }
    }

    /** Emits an arbitrary transition, so a test can drive a consumer of [events]. */
    public fun emit(item: MediaItem, state: DownloadState) {
        _events.tryEmit(DownloadEvent(item, state))
    }

    override fun observeDownloads(): Flow<Map<MediaItemId, DownloadState>> = downloads

    override fun observeDownloaded(): Flow<List<DownloadedMedia>> = completed

    override fun observe(id: MediaItemId): Flow<DownloadState> =
        downloads.map { it[id] ?: DownloadState.NotDownloaded }

    /** The item handed to the most recent [download] — lets a test assert WHICH url was used. */
    public var lastItem: PlayableItem? = null
        private set

    override suspend fun download(item: PlayableItem, audioOnly: Boolean) {
        val media = item.item
        val path = "/fake/${media.id.value}.media"
        requested.add(media.id to audioOnly)
        lastItem = item
        downloads.update { it + (media.id to DownloadState.Downloaded(path, audioOnly = audioOnly)) }
        completed.update { it.filterNot { done -> done.item.id == media.id } + DownloadedMedia(item, path, audioOnly) }
        _events.tryEmit(DownloadEvent(media, DownloadState.Downloaded(path, audioOnly)))
    }

    /** Every item asked for, in order, with the variant requested — for assertions. */
    public val requested: MutableList<Pair<MediaItemId, Boolean>> = mutableListOf()

    override suspend fun delete(id: MediaItemId) {
        downloads.update { it - id }
        completed.update { done -> done.filterNot { it.item.id == id } }
    }
}
