package com.dewijones92.uniapp.di

import android.content.Context
import com.dewijones92.uniapp.account.SharedPrefsTokenStore
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.DefaultChannelRepository
import com.dewijones92.uniapp.data.channel.SubscriptionImporter
import com.dewijones92.uniapp.data.download.DefaultDownloadManager
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.download.EngineDownloadStrategy
import com.dewijones92.uniapp.data.download.HttpDownloadStrategy
import com.dewijones92.uniapp.data.download.RoutedDownloadStrategy
import com.dewijones92.uniapp.data.net.OkHttpTextFetcher
import com.dewijones92.uniapp.data.podcast.DefaultPodcastRepository
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.search.ItunesPodcastSearchSource
import com.dewijones92.uniapp.data.search.SearchSource
import com.dewijones92.uniapp.data.search.YtDlpVideoSearchSource
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.data.sponsorblock.SponsorBlockSegmentSource
import com.dewijones92.uniapp.database.RoomDownloadStore
import com.dewijones92.uniapp.database.RoomSubscriptionStore
import com.dewijones92.uniapp.database.UniAppDatabase
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.innertube.actions.HttpYouTubeActions
import com.dewijones92.uniapp.innertube.actions.YouTubeActions
import com.dewijones92.uniapp.innertube.auth.HttpYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.comments.HttpYouTubeComments
import com.dewijones92.uniapp.innertube.comments.YouTubeComments
import com.dewijones92.uniapp.innertube.feeds.HttpYouTubeFeeds
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds
import com.dewijones92.uniapp.innertube.subscriptions.HttpYouTubeSubscriptions
import com.dewijones92.uniapp.playback.Media3PlaybackController
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.video.ChannelSubscriptions
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import com.dewijones92.uniapp.video.VideoResolver
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import com.dewijones92.uniapp.ytdlp.chaquopy.ChaquopyYtDlpEngine
import com.dewijones92.uniapp.ytdlp.chaquopy.YtDlpUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's dependency graph. Manual DI: construction is code, errors are compile-time. */
interface AppContainer {
    val podcastRepository: PodcastRepository
    val channelRepository: ChannelRepository
    val ytDlpEngine: YtDlpEngine
    val playbackController: PlaybackController
    val podcastSearchSource: SearchSource
    val videoSearchSource: SearchSource
    val skipSegmentSource: SkipSegmentSource
    val downloadManager: DownloadManager
    val videoResolver: VideoResolver
    val videoPlaybackLauncher: VideoPlaybackLauncher
    val channelSubscriptions: ChannelSubscriptions

    /** The signed-in YouTube account seam (device-code login, token upkeep). */
    val youTubeAccount: YouTubeAccount

    /** Imports the signed-in account's subscriptions into the unified model. */
    val subscriptionImporter: SubscriptionImporter

    /** The signed-in account's video feeds (home, subs, watch later, history). */
    val youTubeFeeds: YouTubeFeeds

    /** A video's comments (public; no sign-in needed). */
    val youTubeComments: YouTubeComments

    /** Authenticated write actions (like, subscribe, comment). */
    val youTubeActions: YouTubeActions

    /**
     * Kick off background upkeep on app start (currently: fetch the latest
     * yt-dlp so YouTube-breaking changes get fixed without an app update).
     * Safe to call on every launch; never blocks and never touches Python.
     */
    fun refreshExtractorEngine()
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val database: UniAppDatabase = UniAppDatabase.build(context)

    override val podcastRepository: PodcastRepository by lazy {
        DefaultPodcastRepository(
            fetcher = textFetcher,
            store = RoomSubscriptionStore(database.podcastDao(), RoomSubscriptionStore.SourceType.PODCAST),
        )
    }

    override val channelRepository: ChannelRepository by lazy {
        DefaultChannelRepository(
            engine = ytDlpEngine,
            store = RoomSubscriptionStore(database.podcastDao(), RoomSubscriptionStore.SourceType.CHANNEL),
        )
    }

    // Shared between the engine (activates a cached wheel) and the updater
    // (downloads into it), so a downloaded yt-dlp takes effect next start.
    private val ytDlpUpdateDir = File(context.filesDir, "ytdlp-update")

    override val ytDlpEngine: YtDlpEngine by lazy {
        ChaquopyYtDlpEngine(context, updateCacheDir = ytDlpUpdateDir)
    }

    private val ytDlpUpdater by lazy { YtDlpUpdater(httpClient, ytDlpUpdateDir) }

    override fun refreshExtractorEngine() {
        applicationScope.launch { ytDlpUpdater.ensureLatest() }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val playbackController: PlaybackController by lazy {
        Media3PlaybackController(context, applicationScope)
    }

    private val textFetcher by lazy { OkHttpTextFetcher(httpClient) }

    override val podcastSearchSource: SearchSource by lazy {
        ItunesPodcastSearchSource(textFetcher)
    }

    override val videoSearchSource: SearchSource by lazy {
        YtDlpVideoSearchSource(ytDlpEngine)
    }

    override val skipSegmentSource: SkipSegmentSource by lazy {
        SponsorBlockSegmentSource(textFetcher)
    }

    override val downloadManager: DownloadManager by lazy {
        DefaultDownloadManager(
            downloadDir = File(context.filesDir, "downloads"),
            store = RoomDownloadStore(database.downloadDao()),
            // Videos resolve+merge through the engine (bundled ffmpeg) and drop
            // SponsorBlock segments; podcast enclosures are a plain HTTP fetch.
            strategy = RoutedDownloadStrategy(
                routes = listOf(
                    { item: MediaItem -> EngineDownloadStrategy.handles(item) } to EngineDownloadStrategy(
                        engine = ytDlpEngine,
                        sponsorBlockCategories = SponsorBlockSegmentSource.CATEGORIES.toSet(),
                    ),
                ),
                fallback = HttpDownloadStrategy(httpClient),
            ),
            scope = applicationScope,
        )
    }

    override val videoResolver: VideoResolver by lazy {
        VideoResolver(ytDlpEngine, skipSegmentSource)
    }

    override val videoPlaybackLauncher: VideoPlaybackLauncher by lazy {
        VideoPlaybackLauncher(videoResolver, playbackController)
    }

    override val channelSubscriptions: ChannelSubscriptions by lazy {
        ChannelSubscriptions(channelRepository, youTubeActions)
    }

    override val youTubeAccount: YouTubeAccount by lazy {
        YouTubeAccount(
            auth = HttpYouTubeAuth(httpClient),
            store = SharedPrefsTokenStore(context),
        )
    }

    private val innerTubeClient by lazy { InnerTubeClient(httpClient) }

    override val subscriptionImporter: SubscriptionImporter by lazy {
        SubscriptionImporter(
            subscriptions = HttpYouTubeSubscriptions(youTubeAccount, innerTubeClient),
            channels = channelRepository,
        )
    }

    override val youTubeFeeds: YouTubeFeeds by lazy {
        HttpYouTubeFeeds(youTubeAccount, innerTubeClient)
    }

    override val youTubeComments: YouTubeComments by lazy {
        HttpYouTubeComments(innerTubeClient)
    }

    override val youTubeActions: YouTubeActions by lazy {
        HttpYouTubeActions(youTubeAccount, innerTubeClient)
    }

    private companion object {
        const val HTTP_TIMEOUT_SECONDS = 20L
    }
}
