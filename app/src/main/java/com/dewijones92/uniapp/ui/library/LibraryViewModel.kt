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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val downloaded: StateFlow<List<DownloadedItem>> = combine(
        repository.observeEpisodes(),
        downloads.observeDownloads(),
    ) { episodes, states ->
        episodes.mapNotNull { episode ->
            (states[episode.id] as? DownloadState.Downloaded)?.let { DownloadedItem(episode, it.localPath) }
        }
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
