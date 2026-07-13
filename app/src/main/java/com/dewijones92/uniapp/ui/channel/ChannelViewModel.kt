package com.dewijones92.uniapp.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.ChannelVideosResult
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.video.ChannelSubscriptions
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs a channel page: its recent uploads (read-only browse — no re-extract
 * beyond the fetch) plus a subscribe/unsubscribe toggle. Videos play and the
 * subscription toggles through the same shared seams every other screen uses,
 * so nothing here is pillar- or screen-specific.
 */
class ChannelViewModel(
    private val source: MediaSource.VideoChannel,
    private val channels: ChannelRepository,
    private val launcher: VideoPlaybackLauncher,
    private val channelSubscriptions: ChannelSubscriptions,
    private val downloads: DownloadManager,
) : ViewModel() {

    data class UiState(
        val title: String,
        val videos: List<MediaItem> = emptyList(),
        val loading: Boolean = true,
        val error: Boolean = false,
        val subscribed: Boolean = false,
        val downloadStates: Map<MediaItemId, DownloadState> = emptyMap(),
        /** Watch URL currently being resolved for playback, if any. */
        val resolving: String? = null,
    )

    private data class FetchState(
        val title: String,
        val videos: List<MediaItem> = emptyList(),
        val loading: Boolean = true,
        val error: Boolean = false,
        val resolving: String? = null,
    )

    private val fetch = MutableStateFlow(FetchState(source.title))

    val uiState: StateFlow<UiState> = combine(
        fetch,
        channels.observeSubscriptions(),
        downloads.observeDownloads(),
    ) { f, subs, downloadStates ->
        UiState(
            title = f.title,
            videos = f.videos,
            loading = f.loading,
            error = f.error,
            resolving = f.resolving,
            subscribed = subs.any { it.source.id == source.id },
            downloadStates = downloadStates,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState(source.title))

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            fetch.update { it.copy(loading = true, error = false) }
            fetch.value = when (val result = channels.fetchChannelVideos(source.channelUrl)) {
                is ChannelVideosResult.Success ->
                    FetchState(title = result.title, videos = result.videos, loading = false)
                is ChannelVideosResult.Failure -> fetch.value.copy(loading = false, error = true)
            }
        }
    }

    fun play(video: MediaItem) {
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch {
            fetch.update { it.copy(resolving = watchUrl.value) }
            launcher.play(watchUrl, video.sourceId)
            fetch.update { it.copy(resolving = null) }
        }
    }

    fun toggleSubscribed() {
        val target = !uiState.value.subscribed
        viewModelScope.launch { channelSubscriptions.setSubscribed(source, target) }
    }

    fun download(video: MediaItem) {
        viewModelScope.launch { downloads.download(video) }
    }

    fun deleteDownload(video: MediaItem) {
        viewModelScope.launch { downloads.delete(video.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer, source: MediaSource.VideoChannel): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ChannelViewModel(
                        source = source,
                        channels = container.channelRepository,
                        launcher = container.videoPlaybackLauncher,
                        channelSubscriptions = container.channelSubscriptions,
                        downloads = container.downloadManager,
                    )
                }
            }
    }
}
