package com.dewijones92.uniapp.ui.podcasts

import com.dewijones92.uniapp.data.download.fake.FakeDownloadManager
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import com.dewijones92.uniapp.ui.podcasts.PodcastsViewModel.Subscribing
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
        viewModel = PodcastsViewModel(
            FakePodcastRepository(),
            FakePlaybackController(),
            FakeDownloadManager(),
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
