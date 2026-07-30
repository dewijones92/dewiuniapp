package com.dewijones92.totum.ui.videos

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.channel.DefaultChannelRepository
import com.dewijones92.totum.data.download.fake.FakeDownloadManager
import com.dewijones92.totum.data.group.FakeSourceGroupStore
import com.dewijones92.totum.data.group.GroupFeed
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.queue.fake.InMemoryQueueStore
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.di.GroupServices
import com.dewijones92.totum.di.YouTubeAccountServices
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceGroup
import com.dewijones92.totum.domain.SourceGroupId
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.innertube.actions.fake.FakeYouTubeActions
import com.dewijones92.totum.innertube.auth.AccessToken
import com.dewijones92.totum.innertube.auth.OAuthTokens
import com.dewijones92.totum.innertube.auth.RefreshToken
import com.dewijones92.totum.innertube.auth.YouTubeAccount
import com.dewijones92.totum.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.totum.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.totum.innertube.feeds.fake.FakeYouTubeFeeds
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import com.dewijones92.totum.playback.fake.FakePlaybackController
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.video.AccountSubscriptions
import com.dewijones92.totum.video.VideoPlaybackLauncher
import com.dewijones92.totum.video.VideoResolver
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Selecting a group shows the group, and does NOT try to page it.
 *
 * A group is fetched whole — every member gives what it has, and there is no continuation
 * to follow — so a paging token would be a promise of a next page that cannot exist. The
 * list would then keep asking for one forever, which is exactly the runaway that cost 80
 * requests at launch on a real account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideosGroupFeedTest {

    private val dispatcher = StandardTestDispatcher()

    // viewModelScope runs on Dispatchers.Main, which does not exist on the JVM until this
    // stands one in — without it `select` launches into nothing and the feed stays empty.
    @Before
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private val channel = MediaSource.VideoChannel(
        SourceId("chan"),
        "A channel",
        HttpUrl.of("https://www.youtube.com/channel/UCaaa"),
    )
    private val group = SourceGroup(SourceGroupId("g"), "Politics", listOf(channel))

    private fun viewModel(scope: kotlinx.coroutines.CoroutineScope): VideosViewModel {
        val engine = FakeYtDlpEngine()
        val account = YouTubeAccount(
            FakeYouTubeAuth(),
            InMemoryTokenStore(OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3_600)),
            nowEpochSeconds = { 0 },
        )
        val queue = PlaybackQueue(
            FakePlaybackController(),
            VideoPlaybackLauncher(
                VideoResolver(engine, SkipSegmentSource { emptyList() }),
                FakePlaybackController(),
                FakeYouTubeWatchHistory(),
                InMemoryPlayHistoryStore(),
            ),
            scope,
            InMemoryQueueStore(),
        )
        return VideosViewModel(
            channels = DefaultChannelRepository(engine),
            accountSubscriptions = AccountSubscriptions(
                FakeYouTubeSubscriptions(),
                FakeYouTubeActions(),
                account,
                scope,
            ),
            queue = queue,
            downloads = FakeDownloadManager(),
            youtube = YouTubeAccountServices(account, FakeYouTubeFeeds(), FakeYouTubeActions()),
            groups = GroupServices(
                FakeSourceGroupStore(listOf(group)),
                GroupFeed { listOf(item("from the group")) },
            ),
        )
    }

    @Test
    fun `selecting a group shows its merged items`() = runTest(dispatcher) {
        val model = viewModel(backgroundScope)
        backgroundScope.launch { model.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        model.select(FeedChoice.Group(group))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("from the group"), model.uiState.value.videos.map { it.title })
    }

    @Test
    fun `a group offers no next page`() = runTest(dispatcher) {
        val model = viewModel(backgroundScope)
        backgroundScope.launch { model.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        model.select(FeedChoice.Group(group))
        testScheduler.advanceUntilIdle()

        assertEquals(false, model.uiState.value.canLoadMore)
    }

    @Test
    fun `the group is what the tab reports it is showing`() = runTest(dispatcher) {
        val model = viewModel(backgroundScope)
        backgroundScope.launch { model.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        model.select(FeedChoice.Group(group))
        testScheduler.advanceUntilIdle()

        assertEquals(FeedChoice.Group(group), model.uiState.value.selected)
    }

    @Test
    fun `a group survives being signed out`() = runTest(dispatcher) {
        // A group can be entirely podcasts, which need no YouTube account. Signing out used
        // to clear the whole feed state, emptying a feed that was working perfectly well.
        val model = viewModel(backgroundScope)
        backgroundScope.launch { model.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        model.select(FeedChoice.Group(group))
        testScheduler.advanceUntilIdle()

        assertEquals(false, model.uiState.value.signedIn)
        assertEquals(listOf("from the group"), model.uiState.value.videos.map { it.title })
    }

    private fun item(title: String) = MediaItem(
        id = MediaItemId(title),
        sourceId = channel.id,
        title = title,
        publishedAt = null,
        duration = null,
    )
}
