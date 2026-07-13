package com.dewijones92.uniapp.ui.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.SubscribeChannelResult
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.feeds.AccountFeed
import com.dewijones92.uniapp.innertube.feeds.FeedResult
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.video.VideoResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class VideosViewModel(
    private val channels: ChannelRepository,
    private val playback: PlaybackController,
    private val resolver: VideoResolver,
    private val downloads: DownloadManager,
    private val account: YouTubeAccount,
    private val feeds: YouTubeFeeds,
) : ViewModel() {

    data class UiState(
        val subscriptions: List<Subscription> = emptyList(),
        val videos: List<MediaItem> = emptyList(),
        val subscribing: Subscribing = Subscribing.Idle,
        /** watchUrl currently being resolved for playback, if any. */
        val resolving: String? = null,
        val downloadStates: Map<MediaItemId, DownloadState> = emptyMap(),
        /** Account feeds are offered only when signed in. */
        val signedIn: Boolean = false,
        /** The selected account feed, or null for the local latest-videos list. */
        val selectedFeed: AccountFeed? = null,
        val feedLoading: Boolean = false,
        val feedError: Boolean = false,
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

    /** Selected account feed and its loaded videos; null feed = local list. */
    private data class FeedState(
        val selected: AccountFeed? = null,
        val loading: Boolean = false,
        val videos: List<MediaItem> = emptyList(),
        val error: Boolean = false,
    )

    private val signedIn = MutableStateFlow(false)
    private val feedState = MutableStateFlow(FeedState())

    init {
        // Signed in, the subscriptions feed (recent uploads) is the useful
        // default; signed out, the screen keeps its local-channels behaviour.
        viewModelScope.launch {
            if (account.isSignedIn()) {
                signedIn.value = true
                selectFeed(AccountFeed.SUBSCRIPTIONS)
            }
        }
    }

    private val flags = combine(subscribing, resolving) { sub, res -> sub to res }
    private val feedView = combine(feedState, signedIn) { feed, si -> feed to si }

    val uiState: StateFlow<UiState> = combine(
        channels.observeSubscriptions(),
        channels.observeVideos(),
        downloads.observeDownloads(),
        flags,
        feedView,
    ) { subs, localVideos, downloadStates, (subscribing, resolving), (feed, isSignedIn) ->
        UiState(
            subscriptions = subs,
            videos = if (feed.selected == null) localVideos else feed.videos,
            subscribing = subscribing,
            resolving = resolving,
            downloadStates = downloadStates,
            signedIn = isSignedIn,
            selectedFeed = feed.selected,
            feedLoading = feed.loading,
            feedError = feed.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun selectFeed(feed: AccountFeed?) {
        feedState.update { it.copy(selected = feed, error = false) }
        if (feed == null) return
        viewModelScope.launch {
            feedState.update { it.copy(loading = true) }
            val result = when (feed) {
                AccountFeed.RECOMMENDED -> feeds.recommended()
                AccountFeed.SUBSCRIPTIONS -> feeds.subscriptionsFeed()
                AccountFeed.WATCH_LATER -> feeds.watchLater()
                AccountFeed.HISTORY -> feeds.history()
            }
            feedState.value = when (result) {
                is FeedResult.Success ->
                    FeedState(feed, loading = false, videos = result.videos.map { it.toMediaItem(feed) })
                FeedResult.SignedOut -> {
                    signedIn.value = false
                    FeedState()
                }
                is FeedResult.Failure -> FeedState(feed, loading = false, error = true)
            }
        }
    }

    private fun FeedVideo.toMediaItem(feed: AccountFeed) = MediaItem(
        id = MediaItemId(videoId),
        sourceId = SourceId("ytfeed:${feed.name}"),
        title = title,
        publishedAt = null,
        duration = durationSeconds?.seconds,
        author = author,
        thumbnailUrl = thumbnailUrl,
        mediaUrl = watchUrl,
    )

    fun subscribe(rawUrl: String) {
        val url = HttpUrl.parse(rawUrl)
        if (url == null) {
            subscribing.value = Subscribing.Error.InvalidUrl
            return
        }
        viewModelScope.launch {
            subscribing.value = Subscribing.InProgress
            subscribing.value = when (channels.subscribe(url)) {
                is SubscribeChannelResult.Subscribed -> Subscribing.Done
                is SubscribeChannelResult.AlreadySubscribed -> Subscribing.Error.AlreadySubscribed
                is SubscribeChannelResult.Failure.Network -> Subscribing.Error.Network
                is SubscribeChannelResult.Failure.NotAChannel -> Subscribing.Error.NotAChannel
            }
        }
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
            playback.play(video, localPath = local)
            return
        }
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch {
            resolving.value = watchUrl.value
            val resolved = resolver.resolve(watchUrl, video.sourceId)
            if (resolved != null) {
                playback.play(resolved.item, skipSegments = resolved.skipSegments)
            }
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
                    playback = container.playbackController,
                    resolver = container.videoResolver,
                    downloads = container.downloadManager,
                    account = container.youTubeAccount,
                    feeds = container.youTubeFeeds,
                )
            }
        }
    }
}
