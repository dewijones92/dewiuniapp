package com.dewijones92.totum.ui.library

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.DownloadedMedia
import com.dewijones92.totum.domain.StorageUsage
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.ui.common.MediaSort
import com.dewijones92.totum.ui.common.TrackedViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows everything available offline — downloaded items across both pillars.
 *
 * The download records are the whole source. This used to combine podcast episodes with
 * download states, so a downloaded video sat on disk and never appeared here.
 */
class LibraryViewModel(
    private val queue: PlaybackQueue,
    private val downloads: DownloadManager,
    private val fileSize: (String) -> Long = { File(it).length() },
    private val freeSpace: () -> Long? = { null },
    /** Injected so a test can drive the sizing pass; it is real disk IO in the app. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TrackedViewModel("library") {

    /** A download with the space it actually occupies on disk. */
    data class Entry(val media: DownloadedMedia, val sizeBytes: Long) {
        val item get() = media.item
    }

    private val sort = MutableStateFlow(MediaSort.DEFAULT)
    val sortOrder: StateFlow<MediaSort> = sort.asStateFlow()

    fun setSort(order: MediaSort) {
        sort.value = order
    }

    val downloaded: StateFlow<List<Entry>> = combine(
        downloads.observeDownloaded(),
        sort,
    ) { items, order ->
        // Sized off the main thread: this stats a file per download, which is cheap but
        // is still disk IO and there is no reason for it to be on the frame path.
        withContext(io) {
            order.sortedBy(items) { it.item }.map { Entry(it, fileSize(it.localPath)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /**
     * What the downloads are costing. Derived from the same list rather than counted
     * separately, so the header can never disagree with the rows under it.
     */
    val storage: StateFlow<StorageUsage> = downloaded
        .map { entries -> StorageUsage(entries.size, entries.sumOf { it.sizeBytes }, freeSpace()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StorageUsage.Empty)

    /** Plays the local file, through the queue like every other tap. */
    fun play(entry: Entry) {
        viewModelScope.launch { queue.playNow(entry.media.offline) }
    }

    fun delete(entry: Entry) {
        viewModelScope.launch { downloads.delete(entry.item.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    queue = container.playbackQueue,
                    downloads = container.downloadManager,
                    freeSpace = container::freeDownloadSpaceBytes,
                )
            }
        }
    }
}
