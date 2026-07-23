package com.dewijones92.uniapp.di

import android.content.Context
import com.dewijones92.uniapp.account.SharedPrefsTokenStore
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.DefaultChannelRepository
import com.dewijones92.uniapp.data.content.ContentRefresher
import com.dewijones92.uniapp.data.content.PodcastSubscriptionItemsSource
import com.dewijones92.uniapp.data.content.SeenItemsTracker
import com.dewijones92.uniapp.data.download.DefaultDownloadManager
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.download.EngineDownloadStrategy
import com.dewijones92.uniapp.data.download.HttpDownloadStrategy
import com.dewijones92.uniapp.data.download.RoutedDownloadStrategy
import com.dewijones92.uniapp.data.importexport.OpmlExporter
import com.dewijones92.uniapp.data.importexport.SubscriptionImportParser
import com.dewijones92.uniapp.data.net.OkHttpTextFetcher
import com.dewijones92.uniapp.data.podcast.DefaultPodcastRepository
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.search.ItunesPodcastSearchSource
import com.dewijones92.uniapp.data.search.SearchSource
import com.dewijones92.uniapp.data.search.YtDlpVideoSearchSource
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.data.sponsorblock.SponsorBlockSegmentSource
import com.dewijones92.uniapp.database.RoomDownloadStore
import com.dewijones92.uniapp.database.RoomPlaybackProgressStore
import com.dewijones92.uniapp.database.RoomSubscriptionStore
import com.dewijones92.uniapp.database.UniAppDatabase
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.importexport.SubscriptionImporter
import com.dewijones92.uniapp.innertube.actions.HttpYouTubeActions
import com.dewijones92.uniapp.innertube.actions.YouTubeActions
import com.dewijones92.uniapp.innertube.auth.HttpYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.comments.HttpYouTubeComments
import com.dewijones92.uniapp.innertube.comments.YouTubeComments
import com.dewijones92.uniapp.innertube.feeds.HttpYouTubeFeeds
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds
import com.dewijones92.uniapp.innertube.history.HttpYouTubeWatchHistory
import com.dewijones92.uniapp.innertube.history.YouTubeWatchHistory
import com.dewijones92.uniapp.innertube.playlists.HttpYouTubePlaylists
import com.dewijones92.uniapp.innertube.playlists.YouTubePlaylists
import com.dewijones92.uniapp.innertube.related.HttpYouTubeRelated
import com.dewijones92.uniapp.innertube.related.YouTubeRelated
import com.dewijones92.uniapp.innertube.subscriptions.HttpYouTubeSubscriptions
import com.dewijones92.uniapp.notifications.SharedPrefsSeenItemsTracker
import com.dewijones92.uniapp.notifications.YouTubeSubscriptionItemsSource
import com.dewijones92.uniapp.playback.Media3PlaybackController
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.playback.SharedPrefsPlaybackSpeedStore
import com.dewijones92.uniapp.playback.SleepTimer
import com.dewijones92.uniapp.queue.PlaybackQueue
import com.dewijones92.uniapp.settings.AppPreferences
import com.dewijones92.uniapp.settings.NetworkStatus
import com.dewijones92.uniapp.settings.SharedPrefsAppPreferences
import com.dewijones92.uniapp.video.AccountSubscriptions
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import com.dewijones92.uniapp.video.VideoResolver
import com.dewijones92.uniapp.video.WatchHistorySync
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

    /** Sleep timer that pauses playback after a chosen delay. */
    val sleepTimer: SleepTimer

    /** The unified up-next queue (what plays after the current item), both pillars. */
    val playbackQueue: PlaybackQueue

    /** User settings (per-network default quality, …). */
    val appPreferences: AppPreferences

    /** The signed-in account's subscribed channels, read live (no local copy). */
    val accountSubscriptions: AccountSubscriptions

    /** The signed-in YouTube account seam (device-code login, token upkeep). */
    val youTubeAccount: YouTubeAccount

    /** The signed-in account's video feeds (home, subs, watch later, history). */
    val youTubeFeeds: YouTubeFeeds

    /** A video's comments (public; no sign-in needed). */
    val youTubeComments: YouTubeComments

    /** A video's related / "up next" list (public; no sign-in needed). */
    val youTubeRelated: YouTubeRelated

    /** Authenticated write actions (like, subscribe, comment). */
    val youTubeActions: YouTubeActions

    val youTubePlaylists: YouTubePlaylists

    /** Seen-state for the in-app bell (new since the user last opened the list). */
    val bellSeenTracker: SeenItemsTracker

    /** Finds new content across both pillars for the background notifications. */
    val contentRefresher: ContentRefresher

    /** Imports subscriptions from other apps (OPML / NewPipe / Takeout) and exports them as OPML. */
    val subscriptionImporter: SubscriptionImporter

    /**
     * Kick off background upkeep on app start (currently: fetch the latest
     * yt-dlp so YouTube-breaking changes get fixed without an app update).
     * Safe to call on every launch; never blocks and never touches Python.
     */
    fun refreshExtractorEngine()

    /**
     * Start mirroring video watch-progress to YouTube's servers as playback
     * advances (History + cross-device resume). No-ops while signed out.
     */
    fun startWatchHistorySync()

    /**
     * Load the signed-in account's subscribed channels into [accountSubscriptions]
     * (read live, never copied). Runs in the background on launch; no-ops while
     * signed out.
     */
    fun refreshSubscriptions()
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
        DefaultChannelRepository(engine = ytDlpEngine)
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
        Media3PlaybackController(
            context,
            applicationScope,
            RoomPlaybackProgressStore(database.playbackProgressDao()),
            SharedPrefsPlaybackSpeedStore(context),
        )
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

    override val sleepTimer: SleepTimer by lazy {
        SleepTimer(playbackController, applicationScope)
    }

    override val playbackQueue: PlaybackQueue by lazy {
        PlaybackQueue(playbackController, videoPlaybackLauncher, applicationScope)
    }

    private val youTubeWatchHistory: YouTubeWatchHistory by lazy {
        HttpYouTubeWatchHistory(youTubeAccount, httpClient)
    }

    override val appPreferences: AppPreferences by lazy { SharedPrefsAppPreferences(context) }

    private val networkStatus by lazy { NetworkStatus(context) }

    override val videoPlaybackLauncher: VideoPlaybackLauncher by lazy {
        VideoPlaybackLauncher(
            videoResolver,
            playbackController,
            youTubeWatchHistory,
            preferredMaxHeight = {
                val settings = appPreferences.settings.value
                if (networkStatus.isMetered()) settings.cellularMaxHeight else settings.wifiMaxHeight
            },
        )
    }

    private val watchHistorySync: WatchHistorySync by lazy {
        WatchHistorySync(playbackController, youTubeWatchHistory, applicationScope)
    }

    override fun startWatchHistorySync() {
        watchHistorySync.start()
    }

    override fun refreshSubscriptions() {
        accountSubscriptions.refresh()
    }

    override val accountSubscriptions: AccountSubscriptions by lazy {
        AccountSubscriptions(
            subscriptions = HttpYouTubeSubscriptions(youTubeAccount, innerTubeClient),
            actions = youTubeActions,
            account = youTubeAccount,
            scope = applicationScope,
        )
    }

    override val youTubeAccount: YouTubeAccount by lazy {
        YouTubeAccount(
            auth = HttpYouTubeAuth(httpClient),
            store = SharedPrefsTokenStore(context),
        )
    }

    private val innerTubeClient by lazy { InnerTubeClient(httpClient) }

    override val youTubeFeeds: YouTubeFeeds by lazy {
        HttpYouTubeFeeds(youTubeAccount, innerTubeClient)
    }

    override val youTubeComments: YouTubeComments by lazy {
        HttpYouTubeComments(innerTubeClient)
    }

    override val youTubeRelated: YouTubeRelated by lazy {
        HttpYouTubeRelated(innerTubeClient)
    }

    override val youTubeActions: YouTubeActions by lazy {
        HttpYouTubeActions(youTubeAccount, innerTubeClient)
    }

    override val youTubePlaylists: YouTubePlaylists by lazy {
        HttpYouTubePlaylists(youTubeAccount, innerTubeClient)
    }

    override val bellSeenTracker: SeenItemsTracker by lazy {
        SharedPrefsSeenItemsTracker(context, namespace = "bell")
    }

    override val contentRefresher: ContentRefresher by lazy {
        ContentRefresher(
            sources = listOf(
                PodcastSubscriptionItemsSource(podcastRepository),
                YouTubeSubscriptionItemsSource(youTubeFeeds),
            ),
            tracker = SharedPrefsSeenItemsTracker(context, namespace = "notifications"),
        )
    }

    override val subscriptionImporter: SubscriptionImporter by lazy {
        SubscriptionImporter(
            parser = SubscriptionImportParser(),
            exporter = OpmlExporter(),
            podcasts = podcastRepository,
            channels = accountSubscriptions,
            channelResolver = channelRepository,
        )
    }

    private companion object {
        const val HTTP_TIMEOUT_SECONDS = 20L
    }
}
