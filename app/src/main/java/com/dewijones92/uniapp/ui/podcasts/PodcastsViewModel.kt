package com.dewijones92.uniapp.ui.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.SubscribeResult
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaKind
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.ui.common.MediaSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PodcastsViewModel(
    private val repository: PodcastRepository,
    private val playback: PlaybackController,
    private val downloads: DownloadManager,
) : ViewModel() {

    data class UiState(
        val subscriptions: List<Subscription> = emptyList(),
        val episodes: List<MediaItem> = emptyList(),
        val subscribing: Subscribing = Subscribing.Idle,
        val downloadStates: Map<MediaItemId, DownloadState> = emptyMap(),
        val refreshing: Boolean = false,
        val sort: MediaSort = MediaSort.DEFAULT,
    )

    /** State of the current subscribe attempt; the dialog renders from this. */
    sealed interface Subscribing {
        data object Idle : Subscribing
        data object InProgress : Subscribing
        data object Done : Subscribing

        sealed interface Error : Subscribing {
            data object InvalidUrl : Error
            data object Network : Error
            data object InvalidFeed : Error
            data object AlreadySubscribed : Error
        }
    }

    private val subscribing = MutableStateFlow<Subscribing>(Subscribing.Idle)
    private val refreshing = MutableStateFlow(false)
    private val sort = MutableStateFlow(MediaSort.DEFAULT)
    private val refreshAndSort = combine(refreshing, sort) { r, s -> r to s }

    val uiState: StateFlow<UiState> = combine(
        repository.observeSubscriptions(),
        repository.observeEpisodes(),
        subscribing,
        downloads.observeDownloads(),
        refreshAndSort,
    ) { subs, episodes, subscribing, downloadStates, (refreshing, sort) ->
        UiState(subs, sort.apply(episodes), subscribing, downloadStates, refreshing, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun setSort(order: MediaSort) {
        sort.value = order
    }

    /** Pull-to-refresh: re-fetch every subscribed feed's episodes. */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            repository.refresh()
            refreshing.value = false
        }
    }

    /** Plays the downloaded file when available, else streams. One decision, one place. */
    fun play(episode: MediaItem) {
        val local = (uiState.value.downloadStates[episode.id] as? DownloadState.Downloaded)?.localPath
        playback.play(episode, kind = MediaKind.PODCAST, localPath = local)
    }

    fun download(episode: MediaItem) {
        viewModelScope.launch { downloads.download(episode) }
    }

    fun deleteDownload(episode: MediaItem) {
        viewModelScope.launch { downloads.delete(episode.id) }
    }

    fun subscribe(rawUrl: String) {
        val url = HttpUrl.parse(rawUrl)
        if (url == null) {
            subscribing.value = Subscribing.Error.InvalidUrl
            return
        }
        viewModelScope.launch {
            subscribing.value = Subscribing.InProgress
            subscribing.value = when (repository.subscribe(url)) {
                is SubscribeResult.Subscribed -> Subscribing.Done
                is SubscribeResult.AlreadySubscribed -> Subscribing.Error.AlreadySubscribed
                is SubscribeResult.Failure.Network -> Subscribing.Error.Network
                is SubscribeResult.Failure.InvalidFeed -> Subscribing.Error.InvalidFeed
            }
        }
    }

    /** Call when the add-podcast dialog closes, so the next attempt starts clean. */
    fun resetSubscribing() {
        subscribing.update { Subscribing.Idle }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PodcastsViewModel(
                    repository = container.podcastRepository,
                    playback = container.playbackController,
                    downloads = container.downloadManager,
                )
            }
        }
    }
}
