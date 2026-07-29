package com.dewijones92.totum.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.data.channel.ChannelRepository
import com.dewijones92.totum.data.channel.ChannelVideosResult
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.isSameChannelAs
import com.dewijones92.totum.domain.youTubeChannelId
import com.dewijones92.totum.innertube.channel.ChannelPlaylists
import com.dewijones92.totum.innertube.channel.ChannelVideos
import com.dewijones92.totum.innertube.channel.YouTubeChannel
import com.dewijones92.totum.innertube.playlists.Playlist
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.ui.common.toMediaItem
import com.dewijones92.totum.video.AccountSubscriptions
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
// The channel page's one view-model: tab selection, paging, play, download, subscribe.
// Each is thin and belongs to this screen, so splitting would scatter it (same reasoning
// as VideosViewModel).
@Suppress("TooManyFunctions")
class ChannelViewModel(
    private val source: MediaSource.VideoChannel,
    private val channel: YouTubeChannel,
    private val channelFallback: ChannelRepository,
    private val queue: PlaybackQueue,
    private val accountSubscriptions: AccountSubscriptions,
    private val downloads: DownloadManager,
) : ViewModel() {

    enum class Tab { VIDEOS, SHORTS, PLAYLISTS, SEARCH }

    /**
     * One tab's load state. Paging lives here rather than in three parallel fields: every
     * tab pages the same way, so the state that describes a tab describes its paging too.
     */
    data class TabState<T>(
        val loading: Boolean = false,
        val error: Boolean = false,
        val loaded: Boolean = false,
        val items: List<T> = emptyList(),
        /** Where the next page starts; null once the tab has no more to give. */
        val next: PageToken? = null,
        val loadingMore: Boolean = false,
    ) {
        val canLoadMore: Boolean get() = next != null && !loadingMore && !loading
    }

    data class UiState(
        val title: String,
        val tab: Tab = Tab.VIDEOS,
        val videos: TabState<MediaItem> = TabState(),
        val shorts: TabState<MediaItem> = TabState(),
        val playlists: TabState<Playlist> = TabState(),
        /** Results of searching within this channel, and the query they belong to. */
        val searchResults: TabState<MediaItem> = TabState(),
        val searchQuery: String = "",
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
        val searchResults: TabState<MediaItem> = TabState(),
        val searchQuery: String = "",
        val resolving: String? = null,
    )

    private val channelId: String? = source.youTubeChannelId

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
            searchResults = c.searchResults,
            searchQuery = c.searchQuery,
            // Compared by CHANNEL, not by URL. The account's subscriptions arrive keyed by their
            // canonical /channel/UC… URL while a channel opened from a video row or a search hit
            // carries whatever form that source used, so string equality reported "not subscribed"
            // for channels Dewi was plainly subscribed to.
            subscribed = subs.any { it.isSameChannelAs(source) },
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
            // Nothing to load until there is a query to load it for.
            Tab.SEARCH -> Unit
        }
    }

    /**
     * Searches within this channel. Blank clears the results rather than searching for
     * nothing, so backspacing out of a query does not leave stale hits on screen.
     */
    fun search(query: String) {
        val trimmed = query.trim()
        content.update { it.copy(searchQuery = query) }
        val id = channelId ?: return
        if (trimmed.isEmpty()) {
            content.update { it.copy(searchResults = TabState()) }
            return
        }
        viewModelScope.launch {
            content.update { it.copy(searchResults = it.searchResults.copy(loading = true, error = false)) }
            val page = videoPage(channel.search(id, trimmed))
            // Dropped if the query moved on while this was in flight.
            if (content.value.searchQuery.trim() != trimmed) return@launch
            content.update {
                it.copy(
                    searchResults = TabState(
                        loaded = true,
                        error = page == null,
                        items = page?.items.orEmpty(),
                        next = page?.next,
                    ),
                )
            }
        }
    }

    private fun loadVideos() {
        viewModelScope.launch {
            content.update { it.copy(videos = it.videos.copy(loading = true, error = false)) }
            val page = channelId?.let { id ->
                when (val r = channel.videos(id)) {
                    is ChannelVideos.Success -> r.page.map { it.toMediaItem(source.id) }
                    is ChannelVideos.Failure -> null
                }
                // The yt-dlp fallback returns everything it found in one go, so it is a last
                // page by nature rather than by omission.
            } ?: fallbackVideos()?.let { Page.last(it) }
            content.update {
                it.copy(
                    videos = TabState(
                        loaded = true,
                        error = page == null,
                        items = page?.items.orEmpty(),
                        next = page?.next,
                    ),
                )
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
            val page = when (val r = channel.shorts(id)) {
                is ChannelVideos.Success -> r.page.map { it.toMediaItem(source.id) }
                is ChannelVideos.Failure -> null
            }
            content.update {
                it.copy(
                    shorts = TabState(
                        loaded = true,
                        error = page == null,
                        items = page?.items.orEmpty(),
                        next = page?.next,
                    ),
                )
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
            val page = when (val r = channel.playlists(id)) {
                is ChannelPlaylists.Success -> r.page
                is ChannelPlaylists.Failure -> null
            }
            content.update {
                it.copy(
                    playlists = TabState(
                        loaded = true,
                        error = page == null,
                        items = page?.items.orEmpty(),
                        next = page?.next,
                    ),
                )
            }
        }
    }

    /**
     * Loads the next page of whichever tab is showing. Self-guarding like every other
     * paged screen: no overlapping requests, a no-op once the tab is exhausted, and a
     * failed page keeps its token so scrolling retries instead of ending the tab.
     */
    fun loadMore() {
        val id = channelId ?: return
        when (content.value.tab) {
            Tab.VIDEOS -> pageMore(
                state = { it.videos },
                update = { c, s -> c.copy(videos = s) },
                fetch = { after -> videoPage(channel.videos(id, after)) },
            )
            Tab.SHORTS -> pageMore(
                state = { it.shorts },
                update = { c, s -> c.copy(shorts = s) },
                fetch = { after -> videoPage(channel.shorts(id, after)) },
            )
            Tab.PLAYLISTS -> pageMore(
                state = { it.playlists },
                update = { c, s -> c.copy(playlists = s) },
                fetch = { after -> (channel.playlists(id, after) as? ChannelPlaylists.Success)?.page },
            )
            Tab.SEARCH -> pageMore(
                state = { it.searchResults },
                update = { c, s -> c.copy(searchResults = s) },
                fetch = { after -> videoPage(channel.search(id, content.value.searchQuery.trim(), after)) },
            )
        }
    }

    /** A tab result as domain items, or null when it failed. */
    private fun videoPage(result: ChannelVideos): Page<MediaItem>? =
        (result as? ChannelVideos.Success)?.page?.map { it.toMediaItem(source.id) }

    /**
     * The paging step, once, for any tab. The three tabs differ only in which state they
     * read and which call they make, so those are the parameters and nothing else is
     * duplicated.
     */
    private fun <T> pageMore(
        state: (Content) -> TabState<T>,
        update: (Content, TabState<T>) -> Content,
        fetch: suspend (PageToken) -> Page<T>?,
    ) {
        val current = state(content.value)
        val after = current.next ?: return
        if (current.loadingMore || current.loading) return
        content.update { update(it, state(it).copy(loadingMore = true)) }
        viewModelScope.launch {
            val fetched = fetch(after)
            content.update { c ->
                val existing = state(c)
                val merged = if (fetched == null) {
                    // Keep the token: one flaky request must not end the tab.
                    existing.copy(loadingMore = false)
                } else {
                    existing.copy(
                        items = existing.items + fetched.items.filterNot { it in existing.items },
                        next = fetched.next,
                        loadingMore = false,
                    )
                }
                update(c, merged)
            }
        }
    }

    fun play(video: MediaItem) {
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch {
            content.update { it.copy(resolving = watchUrl.value) }
            queue.playNow(PlayableItem(video, PlayHandle.Video(watchUrl)))
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
                        queue = container.playbackQueue,
                        accountSubscriptions = container.accountSubscriptions,
                        downloads = container.downloadManager,
                    )
                }
            }
    }
}
