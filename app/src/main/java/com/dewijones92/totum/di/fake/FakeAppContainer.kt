package com.dewijones92.totum.di.fake

import com.dewijones92.totum.backup.BackupService
import com.dewijones92.totum.common.Page
import com.dewijones92.totum.data.channel.ChannelRepository
import com.dewijones92.totum.data.channel.fake.FakeChannelRepository
import com.dewijones92.totum.data.content.ContentRefresher
import com.dewijones92.totum.data.content.SeenItemsTracker
import com.dewijones92.totum.data.content.fake.InMemorySeenItemsTracker
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.data.download.fake.FakeDownloadManager
import com.dewijones92.totum.data.group.FakeSourceGroupStore
import com.dewijones92.totum.data.group.GroupFeed
import com.dewijones92.totum.data.group.SourceGroupStore
import com.dewijones92.totum.data.history.PlayHistoryStore
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.importexport.OpmlExporter
import com.dewijones92.totum.data.importexport.SubscriptionImportParser
import com.dewijones92.totum.data.playlist.LocalPlaylistStore
import com.dewijones92.totum.data.playlist.fake.InMemoryLocalPlaylistStore
import com.dewijones92.totum.data.podcast.PodcastRepository
import com.dewijones92.totum.data.podcast.fake.FakePodcastRepository
import com.dewijones92.totum.data.queue.QueueStore
import com.dewijones92.totum.data.queue.fake.InMemoryQueueStore
import com.dewijones92.totum.data.search.SearchHistoryStore
import com.dewijones92.totum.data.search.SearchOutcome
import com.dewijones92.totum.data.search.SearchSource
import com.dewijones92.totum.data.search.YtDlpVideoSearchSource
import com.dewijones92.totum.data.search.fake.InMemorySearchHistoryStore
import com.dewijones92.totum.data.source.DefaultSourceLocator
import com.dewijones92.totum.data.source.SourceLocator
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.data.subscription.fake.InMemorySubscriptionStore
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.importexport.SubscriptionImporter
import com.dewijones92.totum.innertube.actions.YouTubeActions
import com.dewijones92.totum.innertube.actions.fake.FakeYouTubeActions
import com.dewijones92.totum.innertube.auth.YouTubeAccount
import com.dewijones92.totum.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.totum.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.totum.innertube.channel.YouTubeChannel
import com.dewijones92.totum.innertube.channel.fake.FakeYouTubeChannel
import com.dewijones92.totum.innertube.comments.YouTubeComments
import com.dewijones92.totum.innertube.comments.fake.FakeYouTubeComments
import com.dewijones92.totum.innertube.feeds.YouTubeFeeds
import com.dewijones92.totum.innertube.feeds.fake.FakeYouTubeFeeds
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.innertube.playlists.YouTubePlaylists
import com.dewijones92.totum.innertube.playlists.fake.FakeYouTubePlaylists
import com.dewijones92.totum.innertube.related.YouTubeRelated
import com.dewijones92.totum.innertube.related.fake.FakeYouTubeRelated
import com.dewijones92.totum.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import com.dewijones92.totum.playback.NoOpPlaybackProgressStore
import com.dewijones92.totum.playback.PlaybackController
import com.dewijones92.totum.playback.PlaybackProgressStore
import com.dewijones92.totum.playback.SleepTimer
import com.dewijones92.totum.playback.fake.FakePlaybackController
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.settings.AppPreferences
import com.dewijones92.totum.settings.InMemoryAppPreferences
import com.dewijones92.totum.video.AccountSubscriptions
import com.dewijones92.totum.video.VideoPlaybackLauncher
import com.dewijones92.totum.video.VideoResolver
import com.dewijones92.totum.ytdlp.YtDlpEngine
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** In-memory [AppContainer] for previews and UI tests. */
class FakeAppContainer(
    override val podcastRepository: PodcastRepository = FakePodcastRepository(),
    override val channelRepository: ChannelRepository = FakeChannelRepository(),
    override val ytDlpEngine: YtDlpEngine = FakeYtDlpEngine(),
    override val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    override val playbackController: PlaybackController = FakePlaybackController(),
    override val podcastSearchSource: SearchSource = SearchSource { _, _, _ ->
        SearchOutcome.Success(Page.empty())
    },
    override val videoSearchSource: SearchSource = YtDlpVideoSearchSource(ytDlpEngine),
    override val searchHistoryStore: SearchHistoryStore = InMemorySearchHistoryStore(),
    override val playHistoryStore: PlayHistoryStore = InMemoryPlayHistoryStore(),
    override val playbackProgressStore: PlaybackProgressStore = NoOpPlaybackProgressStore,
    override val sourceLocator: SourceLocator = DefaultSourceLocator(podcastRepository, ytDlpEngine),
    override val skipSegmentSource: SkipSegmentSource = SkipSegmentSource { emptyList() },
    override val downloadManager: DownloadManager = FakeDownloadManager(),
    override val sourceGroupStore: SourceGroupStore = FakeSourceGroupStore(),
    override val groupFeed: GroupFeed = GroupFeed(items = { emptyList() }, locate = { null }),
    override val videoResolver: VideoResolver = VideoResolver(ytDlpEngine, skipSegmentSource),
    override val videoPlaybackLauncher: VideoPlaybackLauncher =
        VideoPlaybackLauncher(videoResolver, playbackController, FakeYouTubeWatchHistory(), playHistoryStore),
    override val sleepTimer: SleepTimer = SleepTimer(playbackController, CoroutineScope(SupervisorJob())),
    override val queueStore: QueueStore = InMemoryQueueStore(),
    override val playbackQueue: PlaybackQueue =
        PlaybackQueue(playbackController, videoPlaybackLauncher, CoroutineScope(SupervisorJob()), queueStore),
    override val localPlaylistStore: LocalPlaylistStore = InMemoryLocalPlaylistStore(),
    override val appPreferences: AppPreferences = InMemoryAppPreferences(),
    override val youTubeAccount: YouTubeAccount = YouTubeAccount(FakeYouTubeAuth(), InMemoryTokenStore()),
    override val accountSubscriptions: AccountSubscriptions = AccountSubscriptions(
        subscriptions = FakeYouTubeSubscriptions(),
        actions = FakeYouTubeActions(),
        account = youTubeAccount,
        scope = CoroutineScope(SupervisorJob()),
    ),
    override val youTubeFeeds: YouTubeFeeds = FakeYouTubeFeeds(),
    override val youTubeComments: YouTubeComments = FakeYouTubeComments(),
    override val youTubeRelated: YouTubeRelated = FakeYouTubeRelated(),
    override val youTubeChannel: YouTubeChannel = FakeYouTubeChannel(),
    override val youTubeActions: YouTubeActions = FakeYouTubeActions(),
    override val youTubePlaylists: YouTubePlaylists = FakeYouTubePlaylists(),
    override val bellSeenTracker: SeenItemsTracker = InMemorySeenItemsTracker(),
    override val contentRefresher: ContentRefresher = ContentRefresher(emptyList(), InMemorySeenItemsTracker()),
    override val subscriptionImporter: SubscriptionImporter = SubscriptionImporter(
        parser = SubscriptionImportParser(),
        exporter = OpmlExporter(),
        podcasts = podcastRepository,
        channels = accountSubscriptions,
        channelResolver = channelRepository,
    ),
) : AppContainer {
    override fun refreshExtractorEngine() = Unit

    override fun startQueueAutoDownload() = Unit

    override fun startDownloadNotifications() = Unit

    override fun installCrashReporting() = Unit

    override fun sendDiagnostics(note: String) = Unit

    override fun startWatchHistorySync() = Unit

    override fun refreshSubscriptions() = Unit

    override fun freeDownloadSpaceBytes(): Long? = null
    override val backupService: BackupService = BackupService(
        subscriptions = InMemorySubscriptionStore(),
        playlists = InMemoryLocalPlaylistStore(),
        queueStore = InMemoryQueueStore(),
        progress = NoOpPlaybackProgressStore,
        settings = object : BackupService.BackupSettings {
            override fun export(): Map<String, String> = emptyMap()
            override fun restore(values: Map<String, String>) = Unit
        },
        appVersion = "preview",
    )
}
