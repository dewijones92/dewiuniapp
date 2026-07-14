package com.dewijones92.uniapp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.ui.common.MediaSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shows everything available offline — downloaded items across both pillars. */
class LibraryViewModel(
    repository: PodcastRepository,
    private val playback: PlaybackController,
    private val downloads: DownloadManager,
) : ViewModel() {

    data class DownloadedItem(val item: MediaItem, val localPath: String)

    private val sort = MutableStateFlow(MediaSort.DEFAULT)
    val sortOrder: StateFlow<MediaSort> = sort.asStateFlow()

    fun setSort(order: MediaSort) {
        sort.value = order
    }

    val downloaded: StateFlow<List<DownloadedItem>> = combine(
        repository.observeEpisodes(),
        downloads.observeDownloads(),
        sort,
    ) { episodes, states, sort ->
        val items = episodes.mapNotNull { episode ->
            (states[episode.id] as? DownloadState.Downloaded)?.let { DownloadedItem(episode, it.localPath) }
        }
        sort.sortedBy(items) { it.item }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun play(entry: DownloadedItem) {
        playback.play(entry.item, localPath = entry.localPath)
    }

    fun delete(entry: DownloadedItem) {
        viewModelScope.launch { downloads.delete(entry.item.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    repository = container.podcastRepository,
                    playback = container.playbackController,
                    downloads = container.downloadManager,
                )
            }
        }
    }
}
