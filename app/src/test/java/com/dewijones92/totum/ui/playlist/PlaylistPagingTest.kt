package com.dewijones92.totum.ui.playlist

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.data.download.fake.FakeDownloadManager
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.queue.fake.InMemoryQueueStore
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.innertube.feeds.FeedVideo
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.innertube.playlists.PlaylistVideosResult
import com.dewijones92.totum.innertube.playlists.fake.FakeYouTubePlaylists
import com.dewijones92.totum.playback.fake.FakePlaybackController
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.video.VideoPlaybackLauncher
import com.dewijones92.totum.video.VideoResolver
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
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
 * Paging a YouTube playlist. The continuation was parsed and then thrown away, so a
 * 184-video playlist rendered as a 20-video one with no indication anything was missing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPagingTest {

    private val dispatcher = StandardTestDispatcher()
    private val playlists = FakeYouTubePlaylists()
    private val downloads = FakeDownloadManager()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = PlaylistViewModel(
        browseId = "PL123",
        title = "A playlist",
        playlists = playlists,
        queue = PlaybackQueue(
            FakePlaybackController(),
            launcher(),
            backgroundScope,
            InMemoryQueueStore(),
        ),
        downloads = downloads,
    )

    @Test
    fun `a playlist with a continuation says it can load more`() = runTest(dispatcher) {
        playlists.videos = success(listOf(video("a")), next = "TOKEN")
        val model = viewModel()
        // WhileSubscribed emits nothing without a collector, so uiState would read as its
        // initial (empty) value — this bit three tests before the subscription was added.
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(1, state.videos.size)
        assertTrue("should offer more", state.canLoadMore)
    }

    @Test
    fun `the last page does not offer more`() = runTest(dispatcher) {
        playlists.videos = success(listOf(video("a")), next = null)
        val model = viewModel()
        // WhileSubscribed emits nothing without a collector, so uiState would read as its
        // initial (empty) value — this bit three tests before the subscription was added.
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(model.uiState.value.canLoadMore)
    }

    @Test
    fun `loading more appends the next page and threads the token`() = runTest(dispatcher) {
        playlists.videos = success(listOf(video("a")), next = "TOKEN")
        val model = viewModel()
        // WhileSubscribed emits nothing without a collector, so uiState would read as its
        // initial (empty) value — this bit three tests before the subscription was added.
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        playlists.videos = success(listOf(video("b")), next = null)
        model.loadMore()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(listOf("a", "b"), state.videos.map { it.id.value })
        assertFalse("no further page", state.canLoadMore)
        // The second call must carry the first page's token, or it just refetches page one.
        assertEquals(listOf(null, PageToken("TOKEN")), playlists.requestedTokens)
    }

    /** YouTube returns overlapping pages, and a duplicate LazyColumn key is a crash. */
    @Test
    fun `an overlapping page does not duplicate items`() = runTest(dispatcher) {
        playlists.videos = success(listOf(video("a"), video("b")), next = "TOKEN")
        val model = viewModel()
        // WhileSubscribed emits nothing without a collector, so uiState would read as its
        // initial (empty) value — this bit three tests before the subscription was added.
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        playlists.videos = success(listOf(video("b"), video("c")), next = null)
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), model.uiState.value.videos.map { it.id.value })
    }

    @Test
    fun `loading more without a token does nothing`() = runTest(dispatcher) {
        playlists.videos = success(listOf(video("a")), next = null)
        val model = viewModel()
        // WhileSubscribed emits nothing without a collector, so uiState would read as its
        // initial (empty) value — this bit three tests before the subscription was added.
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        playlists.requestedTokens.clear()

        model.loadMore()
        advanceUntilIdle()

        assertEquals(emptyList<PageToken?>(), playlists.requestedTokens)
    }

    /** A failed page keeps what is already on screen rather than emptying the list. */
    @Test
    fun `a failed page keeps the items already loaded`() = runTest(dispatcher) {
        playlists.videos = success(listOf(video("a")), next = "TOKEN")
        val model = viewModel()
        // WhileSubscribed emits nothing without a collector, so uiState would read as its
        // initial (empty) value — this bit three tests before the subscription was added.
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        playlists.videos = PlaylistVideosResult.Failure("boom")
        model.loadMore()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(listOf("a"), state.videos.map { it.id.value })
        assertFalse("spinner must clear", state.loadingMore)
    }

    private fun success(videos: List<FeedVideo>, next: String?) =
        PlaylistVideosResult.Success(Page(videos, next?.let(::PageToken)))

    private fun video(id: String) = FeedVideo(
        videoId = id,
        title = id,
        author = null,
        durationSeconds = null,
        thumbnailUrl = null,
        watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=$id"),
    )

    private fun launcher() = VideoPlaybackLauncher(
        VideoResolver(FakeYtDlpEngine(), SkipSegmentSource { emptyList() }),
        FakePlaybackController(),
        FakeYouTubeWatchHistory(),
        InMemoryPlayHistoryStore(),
    )
}
