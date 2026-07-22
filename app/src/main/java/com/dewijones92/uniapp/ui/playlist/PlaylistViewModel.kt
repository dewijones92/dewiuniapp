package com.dewijones92.uniapp.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.playlists.PlaylistVideosResult
import com.dewijones92.uniapp.innertube.playlists.YouTubePlaylists
import com.dewijones92.uniapp.ui.common.MediaSort
import com.dewijones92.uniapp.ui.common.toMediaItem
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs a playlist page: its videos, read live from the account. Videos play and
 * download through the same shared seams every other list uses.
 */
class PlaylistViewModel(
    private val browseId: String,
    title: String,
    private val playlists: YouTubePlaylists,
    private val launcher: VideoPlaybackLauncher,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val sourceId = SourceId("ytplaylist:$browseId")

    data class UiState(
        val title: String,
        val videos: List<MediaItem> = emptyList(),
        val loading: Boolean = true,
        val error: Boolean = false,
        val downloadStates: Map<MediaItemId, DownloadState> = emptyMap(),
        val refreshing: Boolean = false,
        val sort: MediaSort = MediaSort.DEFAULT,
    )

    private data class FetchState(
        val videos: List<MediaItem> = emptyList(),
        val loading: Boolean = true,
        val error: Boolean = false,
    )

    private val fetch = MutableStateFlow(FetchState())
    private val refreshing = MutableStateFlow(false)
    private val sort = MutableStateFlow(MediaSort.DEFAULT)

    val uiState: StateFlow<UiState> = combine(
        fetch,
        downloads.observeDownloads(),
        refreshing,
        sort,
    ) { f, downloadStates, refreshing, sort ->
        UiState(
            title = title,
            videos = sort.apply(f.videos),
            loading = f.loading,
            error = f.error,
            downloadStates = downloadStates,
            refreshing = refreshing,
            sort = sort,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState(title))

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            fetch.update { it.copy(loading = true, error = false) }
            fetch.value = fetchState()
        }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            fetchState().takeIf { !it.error }?.let { fetch.value = it }
            refreshing.value = false
        }
    }

    private suspend fun fetchState(): FetchState = when (val result = playlists.videosIn(browseId)) {
        is PlaylistVideosResult.Success ->
            FetchState(videos = result.videos.map { it.toMediaItem(sourceId) }, loading = false)
        else -> FetchState(videos = fetch.value.videos, loading = false, error = true)
    }

    fun setSort(order: MediaSort) {
        sort.value = order
    }

    fun play(video: MediaItem) {
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch { launcher.play(watchUrl, video.sourceId) }
    }

    fun download(video: MediaItem) {
        viewModelScope.launch { downloads.download(video) }
    }

    fun deleteDownload(video: MediaItem) {
        viewModelScope.launch { downloads.delete(video.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer, browseId: String, title: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlaylistViewModel(
                        browseId = browseId,
                        title = title,
                        playlists = container.youTubePlaylists,
                        launcher = container.videoPlaybackLauncher,
                        downloads = container.downloadManager,
                    )
                }
            }
    }
}
