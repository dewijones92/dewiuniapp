package com.dewijones92.totum.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.DownloadedMedia
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.ui.common.MediaSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shows everything available offline — downloaded items across both pillars.
 *
 * The download records are the whole source. This used to combine podcast episodes with
 * download states, so a downloaded video sat on disk and never appeared here.
 */
class LibraryViewModel(
    private val queue: PlaybackQueue,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val sort = MutableStateFlow(MediaSort.DEFAULT)
    val sortOrder: StateFlow<MediaSort> = sort.asStateFlow()

    fun setSort(order: MediaSort) {
        sort.value = order
    }

    val downloaded: StateFlow<List<DownloadedMedia>> = combine(
        downloads.observeDownloaded(),
        sort,
    ) { items, order ->
        order.sortedBy(items) { it.item }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Plays the local file, through the queue like every other tap. */
    fun play(entry: DownloadedMedia) {
        viewModelScope.launch { queue.playNow(entry.offline) }
    }

    fun delete(entry: DownloadedMedia) {
        viewModelScope.launch { downloads.delete(entry.item.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    queue = container.playbackQueue,
                    downloads = container.downloadManager,
                )
            }
        }
    }
}
