package com.dewijones92.totum.ui.podcasts

import com.dewijones92.totum.data.download.fake.FakeDownloadManager
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.podcast.fake.FakePodcastRepository
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.playback.fake.FakePlaybackController
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.ui.podcasts.PodcastsViewModel.Subscribing
import com.dewijones92.totum.video.VideoPlaybackLauncher
import com.dewijones92.totum.video.VideoResolver
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PodcastsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val controller = FakePlaybackController()
        val launcher = VideoPlaybackLauncher(
            VideoResolver(FakeYtDlpEngine(), SkipSegmentSource { emptyList() }),
            controller,
            FakeYouTubeWatchHistory(),
            InMemoryPlayHistoryStore(),
        )
        viewModel = PodcastsViewModel(
            FakePodcastRepository(),
            controller,
            FakeDownloadManager(),
            PlaybackQueue(controller, launcher, CoroutineScope(dispatcher)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid url is rejected without touching the repository`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.subscribe("not a url")
        advanceUntilIdle()

        assertEquals(Subscribing.Error.InvalidUrl, viewModel.uiState.value.subscribing)
        assertEquals(0, viewModel.uiState.value.subscriptions.size)
    }

    @Test
    fun `successful subscribe lands in Done with the new subscription visible`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.subscribe("https://podcast.example.com/feed.xml")
        advanceUntilIdle()

        assertEquals(Subscribing.Done, viewModel.uiState.value.subscribing)
        assertEquals(1, viewModel.uiState.value.subscriptions.size)
        assertEquals(1, viewModel.uiState.value.episodes.size)
    }

    @Test
    fun `subscribing to the same feed twice surfaces AlreadySubscribed`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.subscribe("https://podcast.example.com/feed.xml")
        advanceUntilIdle()
        viewModel.subscribe("https://podcast.example.com/feed.xml")
        advanceUntilIdle()

        assertEquals(Subscribing.Error.AlreadySubscribed, viewModel.uiState.value.subscribing)
    }

    @Test
    fun `resetSubscribing returns the dialog state to Idle`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.subscribe("not a url")
        advanceUntilIdle()
        viewModel.resetSubscribing()
        advanceUntilIdle()

        assertEquals(Subscribing.Idle, viewModel.uiState.value.subscribing)
    }
}
