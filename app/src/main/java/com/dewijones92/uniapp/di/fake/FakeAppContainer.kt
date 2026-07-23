package com.dewijones92.uniapp.di.fake

import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.fake.FakeChannelRepository
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.download.fake.FakeDownloadManager
import com.dewijones92.uniapp.data.importexport.OpmlExporter
import com.dewijones92.uniapp.data.importexport.SubscriptionImportParser
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.data.search.SearchOutcome
import com.dewijones92.uniapp.data.search.SearchSource
import com.dewijones92.uniapp.data.search.YtDlpVideoSearchSource
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.importexport.SubscriptionImporter
import com.dewijones92.uniapp.innertube.actions.YouTubeActions
import com.dewijones92.uniapp.innertube.actions.fake.FakeYouTubeActions
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.uniapp.innertube.comments.YouTubeComments
import com.dewijones92.uniapp.innertube.comments.fake.FakeYouTubeComments
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds
import com.dewijones92.uniapp.innertube.feeds.fake.FakeYouTubeFeeds
import com.dewijones92.uniapp.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.uniapp.innertube.playlists.YouTubePlaylists
import com.dewijones92.uniapp.innertube.playlists.fake.FakeYouTubePlaylists
import com.dewijones92.uniapp.innertube.related.YouTubeRelated
import com.dewijones92.uniapp.innertube.related.fake.FakeYouTubeRelated
import com.dewijones92.uniapp.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import com.dewijones92.uniapp.notifications.InMemoryNewUploadsTracker
import com.dewijones92.uniapp.notifications.NewUploadsTracker
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.playback.SleepTimer
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import com.dewijones92.uniapp.queue.PlaybackQueue
import com.dewijones92.uniapp.settings.AppPreferences
import com.dewijones92.uniapp.settings.InMemoryAppPreferences
import com.dewijones92.uniapp.video.AccountSubscriptions
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import com.dewijones92.uniapp.video.VideoResolver
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** In-memory [AppContainer] for previews and UI tests. */
class FakeAppContainer(
    override val podcastRepository: PodcastRepository = FakePodcastRepository(),
    override val channelRepository: ChannelRepository = FakeChannelRepository(),
    override val ytDlpEngine: YtDlpEngine = FakeYtDlpEngine(),
    override val playbackController: PlaybackController = FakePlaybackController(),
    override val podcastSearchSource: SearchSource = SearchSource { _, _ ->
        SearchOutcome.Success(emptyList())
    },
    override val videoSearchSource: SearchSource = YtDlpVideoSearchSource(ytDlpEngine),
    override val skipSegmentSource: SkipSegmentSource = SkipSegmentSource { emptyList() },
    override val downloadManager: DownloadManager = FakeDownloadManager(),
    override val videoResolver: VideoResolver = VideoResolver(ytDlpEngine, skipSegmentSource),
    override val videoPlaybackLauncher: VideoPlaybackLauncher =
        VideoPlaybackLauncher(videoResolver, playbackController, FakeYouTubeWatchHistory()),
    override val sleepTimer: SleepTimer = SleepTimer(playbackController, CoroutineScope(SupervisorJob())),
    override val playbackQueue: PlaybackQueue =
        PlaybackQueue(playbackController, videoPlaybackLauncher, CoroutineScope(SupervisorJob())),
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
    override val youTubeActions: YouTubeActions = FakeYouTubeActions(),
    override val youTubePlaylists: YouTubePlaylists = FakeYouTubePlaylists(),
    override val newUploadsTracker: NewUploadsTracker = InMemoryNewUploadsTracker(),
    override val subscriptionImporter: SubscriptionImporter = SubscriptionImporter(
        parser = SubscriptionImportParser(),
        exporter = OpmlExporter(),
        podcasts = podcastRepository,
        channels = accountSubscriptions,
        channelResolver = channelRepository,
    ),
) : AppContainer {
    override fun refreshExtractorEngine() = Unit

    override fun startWatchHistorySync() = Unit

    override fun refreshSubscriptions() = Unit
}
