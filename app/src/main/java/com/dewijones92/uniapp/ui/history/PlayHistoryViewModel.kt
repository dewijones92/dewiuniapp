package com.dewijones92.uniapp.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.history.PlayHistoryStore
import com.dewijones92.uniapp.data.playlist.PlaylistItem
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.playlist.toQueuedItem
import com.dewijones92.uniapp.queue.PlaybackQueue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Recently-played items across both pillars; tap to replay, or clear the lot. */
class PlayHistoryViewModel(
    private val store: PlayHistoryStore,
    private val queue: PlaybackQueue,
    private val downloads: DownloadManager,
) : ViewModel() {

    val items: StateFlow<List<PlaylistItem>> = store.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val downloadStates: StateFlow<Map<MediaItemId, DownloadState>> = downloads.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    /** Replays a single history entry (re-resolving videos through the launcher). */
    fun play(item: PlaylistItem) {
        queue.playAll(listOf(item.toQueuedItem()))
    }

    fun download(item: MediaItem) {
        viewModelScope.launch { downloads.download(item) }
    }

    fun deleteDownload(itemId: MediaItemId) {
        viewModelScope.launch { downloads.delete(itemId) }
    }

    fun clear() {
        viewModelScope.launch { store.clear() }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayHistoryViewModel(
                    store = container.playHistoryStore,
                    queue = container.playbackQueue,
                    downloads = container.downloadManager,
                )
            }
        }
    }
}
