package com.dewijones92.uniapp.ui.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.ChannelVideosResult
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.di.YouTubeAccountServices
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaContentKind
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.feeds.AccountFeed
import com.dewijones92.uniapp.innertube.feeds.FeedResult
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.subscriptions.SubscribedChannel
import com.dewijones92.uniapp.ui.common.MediaSort
import com.dewijones92.uniapp.video.AccountSubscriptions
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

// The Videos tab's one view-model: several small user actions (play, download,
// subscribe, sort, refresh, feed selection) push it just past the counter; they
// are all thin and cohesive, so splitting would only scatter the screen's logic.
@Suppress("TooManyFunctions")
class VideosViewModel(
    private val channels: ChannelRepository,
    private val accountSubscriptions: AccountSubscriptions,
    private val launcher: VideoPlaybackLauncher,
    private val downloads: DownloadManager,
    private val youtube: YouTubeAccountServices,
) : ViewModel() {

    data class UiState(
        /** The signed-in account's subscribed channels, read live from YouTube. */
        val subscriptions: List<MediaSource.VideoChannel> = emptyList(),
        val videos: List<MediaItem> = emptyList(),
        val subscribing: Subscribing = Subscribing.Idle,
        /** watchUrl currently being resolved for playback, if any. */
        val resolving: String? = null,
        val downloadStates: Map<MediaItemId, DownloadState> = emptyMap(),
        /** Account feeds are offered only when signed in. */
        val signedIn: Boolean = false,
        val selectedFeed: AccountFeed? = null,
        val feedLoading: Boolean = false,
        val feedError: Boolean = false,
        /** Pull-to-refresh in progress (keeps content visible, unlike [feedLoading]). */
        val refreshing: Boolean = false,
        val sort: MediaSort = MediaSort.DEFAULT,
    )

    sealed interface Subscribing {
        data object Idle : Subscribing
        data object InProgress : Subscribing
        data object Done : Subscribing

        sealed interface Error : Subscribing {
            data object InvalidUrl : Error
            data object Network : Error
            data object NotAChannel : Error
            data object AlreadySubscribed : Error
        }
    }

    private val subscribing = MutableStateFlow<Subscribing>(Subscribing.Idle)
    private val resolving = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val sort = MutableStateFlow(MediaSort.DEFAULT)

    private data class FeedState(
        val selected: AccountFeed? = null,
        val loading: Boolean = false,
        val videos: List<MediaItem> = emptyList(),
        val error: Boolean = false,
    )

    private val feedState = MutableStateFlow(FeedState())

    init {
        // React to sign-in state — including signing in mid-session — off the
        // one live subscriptions seam: load the subscriptions feed (recent
        // uploads) as the default when signed in, clear it when signed out.
        viewModelScope.launch {
            accountSubscriptions.signedIn.collect { signed ->
                if (signed) {
                    if (feedState.value.selected == null) selectFeed(AccountFeed.SUBSCRIPTIONS)
                } else {
                    feedState.value = FeedState()
                }
            }
        }
    }

    private val flags = combine(subscribing, resolving, refreshing) { sub, res, ref -> Triple(sub, res, ref) }
    private val feedView =
        combine(feedState, accountSubscriptions.signedIn, sort) { feed, si, s -> Triple(feed, si, s) }

    val uiState: StateFlow<UiState> = combine(
        accountSubscriptions.channels,
        downloads.observeDownloads(),
        flags,
        feedView,
    ) { subs, downloadStates, (subscribing, resolving, refreshing), (feed, isSignedIn, sort) ->
        UiState(
            subscriptions = subs,
            videos = sort.apply(feed.videos),
            subscribing = subscribing,
            resolving = resolving,
            downloadStates = downloadStates,
            signedIn = isSignedIn,
            selectedFeed = feed.selected,
            feedLoading = feed.loading,
            feedError = feed.error,
            refreshing = refreshing,
            sort = sort,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun setSort(order: MediaSort) {
        sort.value = order
    }

    fun selectFeed(feed: AccountFeed?) {
        feedState.update { it.copy(selected = feed, error = false) }
        if (feed == null) return
        viewModelScope.launch {
            feedState.update { it.copy(loading = true) }
            feedState.value = when (val result = loadFeed(feed)) {
                is FeedResult.Success ->
                    FeedState(feed, loading = false, videos = result.videos.map { it.toMediaItem(feed) })
                FeedResult.SignedOut -> {
                    // Token died mid-session — re-check, which clears signedIn app-wide.
                    accountSubscriptions.refresh()
                    FeedState()
                }
                is FeedResult.Failure -> FeedState(feed, loading = false, error = true)
            }
        }
    }

    /**
     * Pull-to-refresh: reload the subscription list and re-fetch the current
     * feed, keeping the visible content until the new data arrives (a transient
     * failure is swallowed rather than replacing the list with an error).
     */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            accountSubscriptions.refresh()
            feedState.value.selected?.let { feed ->
                when (val result = loadFeed(feed)) {
                    is FeedResult.Success -> {
                        val items = result.videos.map { it.toMediaItem(feed) }
                        feedState.update { it.copy(videos = items, error = false) }
                    }
                    FeedResult.SignedOut -> accountSubscriptions.refresh()
                    is FeedResult.Failure -> Unit // keep what's shown
                }
            }
            refreshing.value = false
        }
    }

    private suspend fun loadFeed(feed: AccountFeed): FeedResult = when (feed) {
        AccountFeed.RECOMMENDED -> youtube.feeds.recommended()
        AccountFeed.SUBSCRIPTIONS -> youtube.feeds.subscriptionsFeed()
        AccountFeed.WATCH_LATER -> youtube.feeds.watchLater()
        AccountFeed.HISTORY -> youtube.feeds.history()
    }

    private fun FeedVideo.toMediaItem(feed: AccountFeed) = MediaItem(
        id = MediaItemId(videoId),
        sourceId = SourceId("ytfeed:${feed.name}"),
        title = title,
        publishedAt = null,
        publishedText = publishedText,
        duration = durationSeconds?.seconds,
        author = author,
        thumbnailUrl = thumbnailUrl,
        mediaUrl = watchUrl,
        contentKind = when (kind) {
            FeedVideo.Kind.VIDEO -> MediaContentKind.STANDARD
            FeedVideo.Kind.LIVE -> MediaContentKind.LIVE
            FeedVideo.Kind.SHORT -> MediaContentKind.SHORT
        },
    )

    /**
     * Subscribes to a channel by URL — resolves it to a YouTube channel id and
     * subscribes live on the account (no local copy). The live list updates
     * optimistically, so the new channel appears in the row straight away.
     */
    fun subscribe(rawUrl: String) {
        val url = HttpUrl.parse(rawUrl)
        if (url == null) {
            subscribing.value = Subscribing.Error.InvalidUrl
            return
        }
        viewModelScope.launch {
            subscribing.value = Subscribing.InProgress
            subscribing.value = when (val result = channels.fetchChannelVideos(url)) {
                is ChannelVideosResult.Success -> subscribeResolved(result, fallback = url)
                is ChannelVideosResult.Failure.Network -> Subscribing.Error.Network
                is ChannelVideosResult.Failure.NotAChannel -> Subscribing.Error.NotAChannel
            }
        }
    }

    private suspend fun subscribeResolved(result: ChannelVideosResult.Success, fallback: HttpUrl): Subscribing {
        val canonical = SubscribedChannel.channelUrlFor(result.channelId) ?: fallback
        val source = MediaSource.VideoChannel(SourceId(canonical.value), result.title, canonical)
        if (accountSubscriptions.isSubscribed(source.id)) return Subscribing.Error.AlreadySubscribed
        accountSubscriptions.setSubscribed(source, subscribed = true)
        return Subscribing.Done
    }

    fun resetSubscribing() {
        subscribing.update { Subscribing.Idle }
    }

    /**
     * Plays the merged download when it exists (already SponsorBlock-clean, no
     * resolution needed), else resolves the stream and plays it. One decision,
     * one place — mirrors the podcasts pillar.
     */
    fun play(video: MediaItem) {
        val local = (uiState.value.downloadStates[video.id] as? DownloadState.Downloaded)?.localPath
        if (local != null) {
            launcher.playLocal(video, local)
            return
        }
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch {
            resolving.value = watchUrl.value
            launcher.play(watchUrl, video.sourceId)
            resolving.value = null
        }
    }

    fun download(video: MediaItem) {
        viewModelScope.launch { downloads.download(video) }
    }

    fun deleteDownload(video: MediaItem) {
        viewModelScope.launch { downloads.delete(video.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VideosViewModel(
                    channels = container.channelRepository,
                    accountSubscriptions = container.accountSubscriptions,
                    launcher = container.videoPlaybackLauncher,
                    downloads = container.downloadManager,
                    youtube = YouTubeAccountServices(
                        account = container.youTubeAccount,
                        feeds = container.youTubeFeeds,
                        actions = container.youTubeActions,
                    ),
                )
            }
        }
    }
}
