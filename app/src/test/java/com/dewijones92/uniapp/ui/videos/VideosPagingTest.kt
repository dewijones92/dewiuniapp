package com.dewijones92.uniapp.ui.videos

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.common.PageToken
import com.dewijones92.uniapp.data.channel.DefaultChannelRepository
import com.dewijones92.uniapp.data.download.fake.FakeDownloadManager
import com.dewijones92.uniapp.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.uniapp.data.queue.fake.InMemoryQueueStore
import com.dewijones92.uniapp.data.sponsorblock.SkipSegmentSource
import com.dewijones92.uniapp.di.YouTubeAccountServices
import com.dewijones92.uniapp.innertube.actions.fake.FakeYouTubeActions
import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.OAuthTokens
import com.dewijones92.uniapp.innertube.auth.RefreshToken
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.uniapp.innertube.feeds.AccountFeed
import com.dewijones92.uniapp.innertube.feeds.FeedResult
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.feeds.fake.FakeYouTubeFeeds
import com.dewijones92.uniapp.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.uniapp.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import com.dewijones92.uniapp.queue.PlaybackQueue
import com.dewijones92.uniapp.video.AccountSubscriptions
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import com.dewijones92.uniapp.video.VideoResolver
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Paging the Videos tab. This is the behaviour that was silently missing: every feed
 * stopped at page one because continuations were parsed away and never followed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideosPagingTest {

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeYtDlpEngine()
    private val feeds = FakeYouTubeFeeds()

    @Before
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun video(id: String) = FeedVideo(
        videoId = id,
        title = "Video $id",
        author = "Channel",
        durationSeconds = 60,
        thumbnailUrl = null,
        watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=$id"),
    )

    private fun TestScope.viewModel(): VideosViewModel {
        val account = YouTubeAccount(
            FakeYouTubeAuth(),
            InMemoryTokenStore(OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3_600)),
            nowEpochSeconds = { 0 },
        )
        val playback = FakePlaybackController()
        return VideosViewModel(
            channels = DefaultChannelRepository(engine),
            accountSubscriptions = AccountSubscriptions(
                FakeYouTubeSubscriptions(),
                FakeYouTubeActions(),
                account,
                backgroundScope,
            ),
            queue = PlaybackQueue(
                playback,
                VideoPlaybackLauncher(
                    VideoResolver(engine, SkipSegmentSource { emptyList() }),
                    playback,
                    FakeYouTubeWatchHistory(),
                    InMemoryPlayHistoryStore(),
                ),
                backgroundScope,
                InMemoryQueueStore(),
            ),
            downloads = FakeDownloadManager(),
            youtube = YouTubeAccountServices(account, feeds, FakeYouTubeActions()),
        )
    }

    @Test
    fun `a feed with a continuation offers more`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] =
            FeedResult.Success(listOf(video("a")), PageToken("page-2"))

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()

        assertTrue(model.uiState.value.canLoadMore)
    }

    @Test
    fun `a feed without a continuation is finished`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] = FeedResult.Success(listOf(video("a")))

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()

        assertFalse(model.uiState.value.canLoadMore)
    }

    @Test
    fun `loadMore follows the token and appends the next page`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] =
            FeedResult.Success(listOf(video("a")), PageToken("page-2"))
        feeds.pages["page-2"] = FeedResult.Success(listOf(video("b")), PageToken("page-3"))

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("Video a", "Video b"), model.uiState.value.videos.map { it.title })
        assertEquals(PageToken("page-2"), feeds.requested.last().second)
        assertTrue(model.uiState.value.canLoadMore)
    }

    /** YouTube does return overlapping pages; a duplicate LazyColumn key is a crash. */
    @Test
    fun `an overlapping page does not duplicate rows`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] =
            FeedResult.Success(listOf(video("a"), video("b")), PageToken("page-2"))
        feeds.pages["page-2"] = FeedResult.Success(listOf(video("b"), video("c")))

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("Video a", "Video b", "Video c"), model.uiState.value.videos.map { it.title })
    }

    @Test
    fun `loadMore does nothing once the feed is exhausted`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] = FeedResult.Success(listOf(video("a")))

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()
        val requestsAfterFirstPage = feeds.requested.size
        model.loadMore()
        advanceUntilIdle()

        assertEquals(requestsAfterFirstPage, feeds.requested.size)
    }

    /**
     * The list fires "near the end" repeatedly while scrolling. Without a guard that
     * becomes a burst of identical requests and duplicated rows.
     */
    @Test
    fun `overlapping loadMore calls make one request`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] =
            FeedResult.Success(listOf(video("a")), PageToken("page-2"))
        feeds.pages["page-2"] = FeedResult.Success(listOf(video("b")))

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()
        val before = feeds.requested.size
        model.loadMore()
        model.loadMore()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(before + 1, feeds.requested.size)
    }

    /** One flaky request must not permanently end the feed. */
    @Test
    fun `a failed page keeps the token so scrolling retries`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] =
            FeedResult.Success(listOf(video("a")), PageToken("page-2"))
        feeds.pages["page-2"] = FeedResult.Failure("network")

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertTrue(model.uiState.value.canLoadMore)
        assertFalse(model.uiState.value.loadingMore)

        feeds.pages["page-2"] = FeedResult.Success(listOf(video("b")))
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("Video a", "Video b"), model.uiState.value.videos.map { it.title })
    }

    /**
     * A refresh replaces the list, so paging must restart from the refreshed page's
     * token — keeping the old one would append pages continuing a list that's gone.
     */
    @Test
    fun `refresh adopts the new continuation`() = runTest(dispatcher) {
        feeds.results[AccountFeed.SUBSCRIPTIONS] =
            FeedResult.Success(listOf(video("a")), PageToken("page-2"))

        val model = viewModel()
        backgroundScope.launch { model.uiState.collect {} }
        model.selectFeed(AccountFeed.SUBSCRIPTIONS)
        advanceUntilIdle()

        feeds.results[AccountFeed.SUBSCRIPTIONS] =
            FeedResult.Success(listOf(video("z")), PageToken("fresh-2"))
        model.refresh()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(PageToken("fresh-2"), feeds.requested.last().second)
    }
}
