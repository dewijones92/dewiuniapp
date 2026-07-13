package com.dewijones92.uniapp.di.fake

import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.SubscriptionImporter
import com.dewijones92.uniapp.data.channel.fake.FakeChannelRepository
import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.download.fake.FakeDownloadManager
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.data.search.SearchOutcome
import com.dewijones92.uniapp.data.search.SearchSource
import com.dewijones92.uniapp.data.search.YtDlpVideoSearchSource
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds
import com.dewijones92.uniapp.innertube.feeds.fake.FakeYouTubeFeeds
import com.dewijones92.uniapp.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import com.dewijones92.uniapp.video.VideoResolver
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine

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
    override val youTubeAccount: YouTubeAccount = YouTubeAccount(FakeYouTubeAuth(), InMemoryTokenStore()),
    override val subscriptionImporter: SubscriptionImporter =
        SubscriptionImporter(FakeYouTubeSubscriptions(), channelRepository),
    override val youTubeFeeds: YouTubeFeeds = FakeYouTubeFeeds(),
) : AppContainer {
    override fun refreshExtractorEngine() = Unit
}
