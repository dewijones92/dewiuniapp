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
import com.dewijones92.uniapp.innertube.channel.ChannelPlaylists
import com.dewijones92.uniapp.innertube.channel.ChannelVideos
import com.dewijones92.uniapp.innertube.channel.YouTubeChannel
import com.dewijones92.uniapp.innertube.playlists.Playlist
import com.dewijones92.uniapp.ui.common.toMediaItem
import com.dewijones92.uniapp.video.AccountSubscriptions
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the tabbed channel page — Videos / Shorts / Playlists via InnerTube (so
 * videos carry their upload dates) — plus a subscribe toggle. Videos and Shorts
 * play through the same shared launcher every other screen uses. Channels reached
 * without a `UC…` id (a pasted handle) fall back to the yt-dlp uploads list for
 * the Videos tab.
 */
class ChannelViewModel(
    private val source: MediaSource.VideoChannel,
    private val channel: YouTubeChannel,
    private val channelFallback: ChannelRepository,
    private val launcher: VideoPlaybackLauncher,
    private val accountSubscriptions: AccountSubscriptions,
    private val downloads: DownloadManager,
) : ViewModel() {

    enum class Tab { VIDEOS, SHORTS, PLAYLISTS }

    /** One tab's load state. */
    data class TabState<T>(
        val loading: Boolean = false,
        val error: Boolean = false,
        val loaded: Boolean = false,
        val items: List<T> = emptyList(),
    )

    data class UiState(
        val title: String,
        val tab: Tab = Tab.VIDEOS,
        val videos: TabState<MediaItem> = TabState(),
        val shorts: TabState<MediaItem> = TabState(),
        val playlists: TabState<Playlist> = TabState(),
        val subscribed: Boolean = false,
        val downloadStates: Map<MediaItemId, DownloadState> = emptyMap(),
        val resolving: String? = null,
    )

    private data class Content(
        val title: String,
        val tab: Tab = Tab.VIDEOS,
        val videos: TabState<MediaItem> = TabState(),
        val shorts: TabState<MediaItem> = TabState(),
        val playlists: TabState<Playlist> = TabState(),
        val resolving: String? = null,
    )

    private val channelId: String? =
        source.channelUrl.value.substringAfterLast("/channel/", "").ifBlank { null }

    private val content = MutableStateFlow(Content(source.title))

    val uiState: StateFlow<UiState> = combine(
        content,
        accountSubscriptions.channels,
        downloads.observeDownloads(),
    ) { c, subs, downloadStates ->
        UiState(
            title = c.title,
            tab = c.tab,
            videos = c.videos,
            shorts = c.shorts,
            playlists = c.playlists,
            subscribed = subs.any { it.id == source.id },
            downloadStates = downloadStates,
            resolving = c.resolving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState(source.title))

    init {
        loadVideos()
    }

    fun selectTab(tab: Tab) {
        content.update { it.copy(tab = tab) }
        when (tab) {
            Tab.VIDEOS -> if (!content.value.videos.loaded) loadVideos()
            Tab.SHORTS -> if (!content.value.shorts.loaded) loadShorts()
            Tab.PLAYLISTS -> if (!content.value.playlists.loaded) loadPlaylists()
        }
    }

    private fun loadVideos() {
        viewModelScope.launch {
            content.update { it.copy(videos = it.videos.copy(loading = true, error = false)) }
            val items = channelId?.let { id ->
                when (val r = channel.videos(id)) {
                    is ChannelVideos.Success -> r.videos.map { it.toMediaItem(source.id) }
                    is ChannelVideos.Failure -> null
                }
            } ?: fallbackVideos()
            content.update {
                it.copy(videos = TabState(loaded = true, error = items == null, items = items.orEmpty()))
            }
        }
    }

    /** yt-dlp uploads for a channel we can't address by `UC…` id. */
    private suspend fun fallbackVideos(): List<MediaItem>? =
        when (val r = channelFallback.fetchChannelVideos(source.channelUrl)) {
            is ChannelVideosResult.Success -> r.videos
            is ChannelVideosResult.Failure -> null
        }

    private fun loadShorts() {
        val id = channelId ?: run {
            content.update { it.copy(shorts = TabState(loaded = true)) }
            return
        }
        viewModelScope.launch {
            content.update { it.copy(shorts = it.shorts.copy(loading = true, error = false)) }
            val items = when (val r = channel.shorts(id)) {
                is ChannelVideos.Success -> r.videos.map { it.toMediaItem(source.id) }
                is ChannelVideos.Failure -> null
            }
            content.update {
                it.copy(shorts = TabState(loaded = true, error = items == null, items = items.orEmpty()))
            }
        }
    }

    private fun loadPlaylists() {
        val id = channelId ?: run {
            content.update { it.copy(playlists = TabState(loaded = true)) }
            return
        }
        viewModelScope.launch {
            content.update { it.copy(playlists = it.playlists.copy(loading = true, error = false)) }
            val items = when (val r = channel.playlists(id)) {
                is ChannelPlaylists.Success -> r.playlists
                is ChannelPlaylists.Failure -> null
            }
            content.update {
                it.copy(playlists = TabState(loaded = true, error = items == null, items = items.orEmpty()))
            }
        }
    }

    fun play(video: MediaItem) {
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch {
            content.update { it.copy(resolving = watchUrl.value) }
            launcher.play(watchUrl, video.sourceId)
            content.update { it.copy(resolving = null) }
        }
    }

    fun toggleSubscribed() {
        val target = !uiState.value.subscribed
        viewModelScope.launch { accountSubscriptions.setSubscribed(source, target) }
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
                        channel = container.youTubeChannel,
                        channelFallback = container.channelRepository,
                        launcher = container.videoPlaybackLauncher,
                        accountSubscriptions = container.accountSubscriptions,
                        downloads = container.downloadManager,
                    )
                }
            }
    }
}
