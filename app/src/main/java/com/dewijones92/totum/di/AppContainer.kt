package com.dewijones92.totum.di

import android.content.Context
import android.os.StatFs
import com.dewijones92.totum.BuildConfig
import com.dewijones92.totum.account.SharedPrefsTokenStore
import com.dewijones92.totum.backup.BackupService
import com.dewijones92.totum.backup.asBackupSettings
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.data.channel.ChannelRepository
import com.dewijones92.totum.data.channel.DefaultChannelRepository
import com.dewijones92.totum.data.content.ContentRefresher
import com.dewijones92.totum.data.content.PodcastSubscriptionItemsSource
import com.dewijones92.totum.data.content.SeenItemsTracker
import com.dewijones92.totum.data.download.DefaultDownloadManager
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.data.download.EngineDownloadStrategy
import com.dewijones92.totum.data.download.HttpDownloadStrategy
import com.dewijones92.totum.data.download.RoutedDownloadStrategy
import com.dewijones92.totum.data.history.PlayHistoryStore
import com.dewijones92.totum.data.importexport.OpmlExporter
import com.dewijones92.totum.data.importexport.SubscriptionImportParser
import com.dewijones92.totum.data.net.OkHttpTextFetcher
import com.dewijones92.totum.data.playlist.LocalPlaylistStore
import com.dewijones92.totum.data.podcast.DefaultPodcastRepository
import com.dewijones92.totum.data.podcast.PodcastRepository
import com.dewijones92.totum.data.queue.QueueStore
import com.dewijones92.totum.data.search.FallbackSearchSource
import com.dewijones92.totum.data.search.InnerTubeVideoSearchSource
import com.dewijones92.totum.data.search.ItunesPodcastSearchSource
import com.dewijones92.totum.data.search.SearchHistoryStore
import com.dewijones92.totum.data.search.SearchSource
import com.dewijones92.totum.data.search.YtDlpVideoSearchSource
import com.dewijones92.totum.data.source.DefaultSourceLocator
import com.dewijones92.totum.data.source.SourceLocator
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.data.sponsorblock.SponsorBlockSegmentSource
import com.dewijones92.totum.database.RoomDownloadStore
import com.dewijones92.totum.database.RoomLocalPlaylistStore
import com.dewijones92.totum.database.RoomPlayHistoryStore
import com.dewijones92.totum.database.RoomPlaybackProgressStore
import com.dewijones92.totum.database.RoomQueueStore
import com.dewijones92.totum.database.RoomSubscriptionStore
import com.dewijones92.totum.database.TotumDatabase
import com.dewijones92.totum.diagnostics.ActivitySnapshotter
import com.dewijones92.totum.diagnostics.CrashReporter
import com.dewijones92.totum.diagnostics.DiagnosticsUploader
import com.dewijones92.totum.diagnostics.installAndroidLogSink
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.toPlayableOrNull
import com.dewijones92.totum.importexport.SubscriptionImporter
import com.dewijones92.totum.innertube.actions.HttpYouTubeActions
import com.dewijones92.totum.innertube.actions.YouTubeActions
import com.dewijones92.totum.innertube.auth.HttpYouTubeAuth
import com.dewijones92.totum.innertube.auth.YouTubeAccount
import com.dewijones92.totum.innertube.browse.InnerTubeClient
import com.dewijones92.totum.innertube.channel.HttpYouTubeChannel
import com.dewijones92.totum.innertube.channel.YouTubeChannel
import com.dewijones92.totum.innertube.comments.HttpYouTubeComments
import com.dewijones92.totum.innertube.comments.YouTubeComments
import com.dewijones92.totum.innertube.feeds.HttpYouTubeFeeds
import com.dewijones92.totum.innertube.feeds.YouTubeFeeds
import com.dewijones92.totum.innertube.history.HttpYouTubeWatchHistory
import com.dewijones92.totum.innertube.history.YouTubeWatchHistory
import com.dewijones92.totum.innertube.playlists.HttpYouTubePlaylists
import com.dewijones92.totum.innertube.playlists.YouTubePlaylists
import com.dewijones92.totum.innertube.related.HttpYouTubeRelated
import com.dewijones92.totum.innertube.related.RelatedResult
import com.dewijones92.totum.innertube.related.YouTubeRelated
import com.dewijones92.totum.innertube.search.HttpYouTubeSearch
import com.dewijones92.totum.innertube.subscriptions.HttpYouTubeSubscriptions
import com.dewijones92.totum.notifications.DownloadNotifier
import com.dewijones92.totum.notifications.SharedPrefsSeenItemsTracker
import com.dewijones92.totum.notifications.YouTubeSubscriptionItemsSource
import com.dewijones92.totum.playback.AutoAdvancer
import com.dewijones92.totum.playback.ExpiredStreamRecovery
import com.dewijones92.totum.playback.Media3PlaybackController
import com.dewijones92.totum.playback.NextUpPrefetcher
import com.dewijones92.totum.playback.PlaybackController
import com.dewijones92.totum.playback.PlaybackProgressStore
import com.dewijones92.totum.playback.SharedPrefsPlaybackSpeedStore
import com.dewijones92.totum.playback.SharedPrefsVolumeBoostStore
import com.dewijones92.totum.playback.SleepTimer
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.queue.QueueAutoDownloader
import com.dewijones92.totum.search.SharedPrefsSearchHistoryStore
import com.dewijones92.totum.settings.AppPreferences
import com.dewijones92.totum.settings.NetworkStatus
import com.dewijones92.totum.settings.PlaybackMode
import com.dewijones92.totum.settings.SharedPrefsAppPreferences
import com.dewijones92.totum.ui.common.toMediaItem
import com.dewijones92.totum.video.AccountSubscriptions
import com.dewijones92.totum.video.PlatformVideoCodecSupport
import com.dewijones92.totum.video.VideoPlaybackLauncher
import com.dewijones92.totum.video.VideoResolver
import com.dewijones92.totum.video.WatchHistorySync
import com.dewijones92.totum.ytdlp.YtDlpEngine
import com.dewijones92.totum.ytdlp.chaquopy.ChaquopyYtDlpEngine
import com.dewijones92.totum.ytdlp.chaquopy.YtDlpUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's dependency graph. Manual DI: construction is code, errors are compile-time. */
interface AppContainer {
    /**
     * For work that must outlive the screen that started it.
     *
     * Starting playback is the case that matters: a tap kicks off an extraction that takes
     * a second or two, and a composition-bound scope dies the moment the user changes tabs,
     * cancelling it silently. A real report caught exactly that — extraction completed, the
     * user switched tab 1.7s later, and nothing ever played.
     */
    val applicationScope: CoroutineScope

    val podcastRepository: PodcastRepository
    val channelRepository: ChannelRepository
    val ytDlpEngine: YtDlpEngine
    val playbackController: PlaybackController
    val podcastSearchSource: SearchSource
    val videoSearchSource: SearchSource

    /** Recent search queries, offered again in the search screen's idle state. */
    val searchHistoryStore: SearchHistoryStore
    val skipSegmentSource: SkipSegmentSource
    val downloadManager: DownloadManager

    /** Full backup / restore of everything that is not re-downloadable. */
    val backupService: BackupService

    /** Free space where downloads live; null when it cannot be read. */
    fun freeDownloadSpaceBytes(): Long?
    val videoResolver: VideoResolver
    val videoPlaybackLauncher: VideoPlaybackLauncher

    /** Sleep timer that pauses playback after a chosen delay. */
    val sleepTimer: SleepTimer

    /** The unified up-next queue (what plays after the current item), both pillars. */
    val playbackQueue: PlaybackQueue

    /** Persists the queue so it survives a restart. */
    val queueStore: QueueStore

    /**
     * Starts fetching the audio of everything in the queue (honouring the
     * auto-download settings), so the queue is listenable offline.
     */
    fun startQueueAutoDownload()

    /** Starts reporting download progress, completions and failures in the shade. */
    fun startDownloadNotifications()

    /**
     * Installs the crash handler and sends any reports left by a previous run. Called
     * first at startup so a failure during the rest of it is still reported.
     */
    fun installCrashReporting()

    /**
     * Captures the app's current state and event trail and sends it, with no crash
     * involved — for "this behaved wrongly", which is how most bugs actually present.
     */
    fun sendDiagnostics(note: String)

    /** User-curated local playlists, mixing podcasts and videos. */
    val localPlaylistStore: LocalPlaylistStore

    /** Recently-played items across both pillars. */
    val playHistoryStore: PlayHistoryStore

    /** Finds the source (channel / feed) a media row came from, for "go to channel". */
    val sourceLocator: SourceLocator

    /**
     * Resume positions and played/unplayed state. Exposed so every list can label its
     * rows from one source, rather than each screen carrying its own copy.
     */
    val playbackProgressStore: PlaybackProgressStore

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

    /** A channel's public tabs — Videos / Shorts / Playlists (no sign-in needed). */
    val youTubeChannel: YouTubeChannel

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

// The count is the app's whole integration surface — every start*/install* entry point the
// Application calls, plus the few private wire-ups they need. Splitting it would scatter the
// one place the graph is built, which is the point of the class.
@Suppress("TooManyFunctions")
class DefaultAppContainer(private val context: Context) : AppContainer {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val database: TotumDatabase = TotumDatabase.build(context)

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

    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val playbackProgressStore: PlaybackProgressStore by lazy {
        RoomPlaybackProgressStore(database.playbackProgressDao())
    }

    override val playbackController: PlaybackController by lazy {
        Media3PlaybackController(
            context,
            applicationScope,
            playbackProgressStore,
            SharedPrefsPlaybackSpeedStore(context),
            SharedPrefsVolumeBoostStore(context),
            // Podcasts play straight through the controller (their enclosure URL is
            // stable), so record their history here; videos are recorded at the
            // launcher, which knows the stable watch URL.
            onPlay = { item, kind ->
                if (kind == MediaKind.PODCAST) {
                    applicationScope.launch {
                        playHistoryStore.record(PlayableItem(item, PlayHandle.Podcast()))
                    }
                }
            },
        )
    }

    private val textFetcher by lazy { OkHttpTextFetcher(httpClient) }

    override val podcastSearchSource: SearchSource by lazy {
        ItunesPodcastSearchSource(textFetcher)
    }

    override val videoSearchSource: SearchSource by lazy {
        // InnerTube first (it carries upload dates and needs no Python); the
        // engine's ytsearch stays as the fallback if YouTube's shape changes.
        FallbackSearchSource(
            primary = InnerTubeVideoSearchSource(HttpYouTubeSearch(innerTubeClient)),
            fallback = YtDlpVideoSearchSource(ytDlpEngine),
        )
    }

    override val searchHistoryStore: SearchHistoryStore by lazy {
        SharedPrefsSearchHistoryStore(context)
    }

    override val skipSegmentSource: SkipSegmentSource by lazy {
        SponsorBlockSegmentSource(textFetcher) { appPreferences.settings.value.skipCategories }
    }

    override fun freeDownloadSpaceBytes(): Long? =
        runCatching { StatFs(downloadDir.path).availableBytes }.getOrNull()

    private val downloadDir = File(context.filesDir, "downloads")

    override val backupService: BackupService by lazy {
        BackupService(
            subscriptions = RoomSubscriptionStore(database.podcastDao(), RoomSubscriptionStore.SourceType.PODCAST),
            playlists = localPlaylistStore,
            queueStore = queueStore,
            progress = playbackProgressStore,
            settings = appPreferences.asBackupSettings(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    override val downloadManager: DownloadManager by lazy {
        DefaultDownloadManager(
            downloadDir = downloadDir,
            store = RoomDownloadStore(database.downloadDao()),
            // Videos resolve+merge through the engine (bundled ffmpeg) and drop
            // SponsorBlock segments; podcast enclosures are a plain HTTP fetch.
            strategy = RoutedDownloadStrategy(
                video = EngineDownloadStrategy(
                    engine = ytDlpEngine,
                    sponsorBlockCategories = appPreferences.settings.value.skipCategories.mapTo(
                        mutableSetOf()
                    ) { it.id },
                ),
                podcast = HttpDownloadStrategy(httpClient),
            ),
            scope = applicationScope,
        )
    }

    override val videoResolver: VideoResolver by lazy {
        VideoResolver(ytDlpEngine, skipSegmentSource, PlatformVideoCodecSupport())
    }

    override val sleepTimer: SleepTimer by lazy {
        SleepTimer(playbackController, applicationScope)
    }

    override val queueStore: QueueStore by lazy { RoomQueueStore(database.queueDao()) }

    private val crashReporter by lazy { CrashReporter(context, stateProviders = ::diagnosticState) }

    override fun startDownloadNotifications() {
        DownloadNotifier(context, downloadManager, applicationScope).start()
    }

    /**
     * Mirrors a deliberate queue-add to YouTube's Watch Later.
     *
     * Dewi's ask: queueing something here should tell YouTube he likes it, so the algorithm
     * learns from what he lines up rather than only from what he finishes. Watch Later is the
     * right shelf for it — it is literally "I intend to watch this", which is what queueing
     * means — and it is a signal YouTube's own clients send.
     *
     * Only YouTube videos, and only while signed in. A podcast has no YouTube identity, and
     * signed out there is no account to teach. Both are silent no-ops rather than warnings,
     * because neither is a fault.
     */
    private suspend fun saveToWatchLater(queued: PlayableItem) {
        val handle = queued.handle as? PlayHandle.Video ?: return
        if (!accountSubscriptions.signedIn.value) {
            Diag.log("yt-signal", "not saving \"${queued.item.title}\" to Watch Later: signed out")
            return
        }
        val videoId = queued.item.id.value
        val result = youTubeActions.setSavedToWatchLater(videoId, saved = true)
        // Logged either way: this is the proof that queueing reached the account, and a silent
        // failure here would look identical to the feature not existing.
        Diag.log("yt-signal", "watch-later += $videoId -> $result")
    }

    /**
     * Plays the top related video when the queue has run out — the end-of-queue fallback.
     *
     * App-side rather than through the player's view model, which is where it used to live:
     * the queue running dry has nothing to do with whether a screen is on, and reaching into
     * a view model would have put the UI's lifecycle back in the path this change exists to
     * remove.
     */
    private suspend fun playRelatedNext() {
        val playing = playbackQueue.nowPlaying.value ?: return
        val related = youTubeRelated.relatedTo(playing.item.id.value)
        if (related !is RelatedResult.Success) {
            Diag.warn("advance", "no related videos to fall back on")
            return
        }
        val next = related.videos
            .firstOrNull { it.videoId != playing.item.id.value }
            ?.toMediaItem(SourceId("ytrelated"))
            ?.toPlayableOrNull()
        if (next == null) {
            Diag.warn("advance", "related list had nothing playable")
            return
        }
        Diag.log("advance", "queue empty — playing related \"${next.item.title}\"")
        playbackQueue.playNow(next)
    }

    override fun installCrashReporting() {
        installAndroidLogSink()
        crashReporter.install()
        // Turns the event trail into a timeline: transitions alone never show a download
        // stuck at 40%, which is exactly when it is the problem.
        ActivitySnapshotter(playbackController, downloadManager, playbackQueue, applicationScope).start()
        // A signed streaming URL expires in hours, so anything paused overnight comes back
        // to nothing but 403s. Re-resolve and carry on rather than retrying a dead address.
        // Auto-advance is app-scoped for the same reason the recovery is: it must keep
        // working with the screen off. It used to be a composable effect fed by
        // collectAsStateWithLifecycle, which stops collecting when the activity stops — so a
        // phone in a pocket never advanced (proven: a 7-minute gap between an item ending
        // and the decision being reached).
        AutoAdvancer(
            states = playbackController.state,
            advance = { playbackQueue.playNextInQueue() },
            whenQueueEmpty = ::playRelatedNext,
            isEnabled = { appPreferences.settings.value.autoPlayNext },
            scope = applicationScope,
        ).start()
        // Videos resolve just-in-time, which meant yt-dlp's ~7 seconds landed in the silence
        // AFTER the previous item ended. Same rule, started a minute earlier.
        NextUpPrefetcher(
            states = playbackController.state,
            nextUp = playbackQueue::peekNext,
            prefetch = { next ->
                (next.handle as? PlayHandle.Video)?.let { video ->
                    videoResolver.prefetch(video.watchUrl, next.item.sourceId)
                }
            },
            scope = applicationScope,
        ).start()
        ExpiredStreamRecovery(
            failures = playbackController.streamFailures,
            replay = playbackQueue::replayCurrent,
            moveOn = { playbackQueue.playNextInQueue() },
            scope = applicationScope,
        ).start()
        DiagnosticsUploader(context, httpClient, applicationScope).uploadPending()
    }

    /**
     * What the app can say about itself when something goes wrong. Verbose on purpose
     * (Dewi's instruction) — the queue, what's playing and every setting, since those
     * are what a report is usually missing. Each value is computed defensively: a
     * diagnostic must never be the thing that crashes.
     */
    private fun diagnosticState(): Map<String, String> = buildMap {
        runCatching {
            val state = playbackController.state.value
            put("playing.title", state?.title ?: "nothing")
            put("playing.itemId", state?.itemId?.value ?: "-")
            put("playing.kind", state?.kind?.name ?: "-")
            put("playing.positionMs", state?.positionMs?.toString() ?: "-")
            put("playing.hasVideo", state?.hasVideo?.toString() ?: "-")
            put("playing.speed", state?.speed?.toString() ?: "-")
            put("playing.skipSilence", state?.skipSilence?.toString() ?: "-")
            put("playing.volumeBoost", state?.volumeBoost?.name ?: "-")
        }
        runCatching {
            val queue = playbackQueue.state.value
            put("queue.size", queue.entries.size.toString())
            put("queue.currentIndex", queue.currentIndex.toString())
            put("queue.items", queue.entries.joinToString(" | ") { "${it.item.item.title}" })
        }
        runCatching {
            val settings = appPreferences.settings.value
            put("settings.playbackMode", settings.playbackMode.name)
            put("settings.autoPlayNext", settings.autoPlayNext.toString())
            put("settings.autoDownloadQueue", settings.autoDownloadQueue.toString())
            put("settings.autoDownloadWifiOnly", settings.autoDownloadWifiOnly.toString())
            put("settings.wifiMaxHeight", settings.wifiMaxHeight.toString())
            put("settings.cellularMaxHeight", settings.cellularMaxHeight.toString())
        }
        runCatching {
            // The account's subscription list, because "it offered me Subscribe to a channel I
            // follow" is unanswerable without knowing how many channels the app thinks it has.
            val subs = accountSubscriptions.channels.value
            put("account.signedIn", accountSubscriptions.signedIn.value.toString())
            put("account.subscriptions", subs.size.toString())
            put("account.subscriptionTitles", subs.joinToString(" | ") { it.title })
        }
        runCatching { put("network.metered", networkStatus.isMetered().toString()) }
    }

    override fun sendDiagnostics(note: String) {
        crashReporter.reportDiagnostics(note)
        DiagnosticsUploader(context, httpClient, applicationScope).uploadPending()
    }

    override fun startQueueAutoDownload() {
        QueueAutoDownloader(
            queue = playbackQueue.state,
            downloads = downloadManager,
            scope = applicationScope,
            isEnabled = { appPreferences.settings.value.autoDownloadQueue },
            isAllowedOnThisNetwork = {
                !appPreferences.settings.value.autoDownloadWifiOnly || !networkStatus.isMetered()
            },
        ).start()
    }

    override val playbackQueue: PlaybackQueue by lazy {
        PlaybackQueue(
            playbackController,
            videoPlaybackLauncher,
            applicationScope,
            queueStore,
            onQueuedByUser = ::saveToWatchLater,
        )
    }

    override val localPlaylistStore: LocalPlaylistStore by lazy {
        RoomLocalPlaylistStore(database.localPlaylistDao())
    }

    override val sourceLocator: SourceLocator by lazy {
        DefaultSourceLocator(podcastRepository, ytDlpEngine)
    }

    override val playHistoryStore: PlayHistoryStore by lazy {
        RoomPlayHistoryStore(database.playHistoryDao())
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
            playHistory = playHistoryStore,
            // Auto means "video on Wi-Fi, audio on mobile data"; the launcher only ever
            // sees the resolved answer.
            audioPreferred = {
                when (appPreferences.settings.value.playbackMode) {
                    PlaybackMode.AUDIO -> true
                    PlaybackMode.VIDEO -> false
                    PlaybackMode.AUTO -> networkStatus.isMetered()
                }
            },
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

    override val youTubeChannel: YouTubeChannel by lazy {
        HttpYouTubeChannel(innerTubeClient)
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
