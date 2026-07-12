package com.dewijones92.uniapp.ui.search

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.data.search.SearchHit
import com.dewijones92.uniapp.data.search.SearchOutcome
import com.dewijones92.uniapp.data.search.SearchSource
import com.dewijones92.uniapp.data.search.YtDlpVideoSearchSource
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import com.dewijones92.uniapp.ui.search.SearchViewModel.Results
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeYtDlpEngine()
    private val playback = FakePlaybackController()
    private val repository = FakePodcastRepository()

    private val podcastHit = SearchHit.Podcast(
        title = "In Our Time",
        subtitle = "BBC",
        artworkUrl = null,
        feedUrl = HttpUrl.of("https://podcasts.files.bbci.co.uk/b006qykl.rss"),
    )

    private fun viewModel(
        podcastSearch: SearchSource = SearchSource { _, _ -> SearchOutcome.Success(listOf(podcastHit)) },
    ) = SearchViewModel(
        podcastSearch = podcastSearch,
        videoSearch = YtDlpVideoSearchSource(engine),
        podcastRepository = repository,
        engine = engine,
        playback = playback,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search loads both sections`() = runTest(dispatcher) {
        engine.registerSearch("time", listOf(FakeYtDlpEngine.sampleSearchEntry()))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.search("time")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results as Results.Loaded
        assertEquals(listOf(podcastHit), results.podcasts)
        assertEquals(1, results.videos.size)
        assertEquals("Sample result", results.videos[0].title)
    }

    @Test
    fun `one backend failing does not hide the other`() = runTest(dispatcher) {
        engine.registerSearch("time", listOf(FakeYtDlpEngine.sampleSearchEntry()))
        val viewModel = viewModel(podcastSearch = SearchSource { _, _ -> SearchOutcome.Failure("down") })
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.search("time")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results as Results.Loaded
        assertTrue(results.podcastsFailed)
        assertEquals(1, results.videos.size)
    }

    @Test
    fun `subscribing from search lands in subscriptions and marks the hit`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.subscribe(podcastHit)
        advanceUntilIdle()

        assertTrue(podcastHit.feedUrl.value in viewModel.uiState.value.subscribedFeeds)
    }

    @Test
    fun `playing a video hit resolves the stream through the engine`() = runTest(dispatcher) {
        val entry = FakeYtDlpEngine.sampleSearchEntry(id = "v9", title = "Playable")
        engine.registerMedia(entry.watchUrl, FakeYtDlpEngine.sampleMetadata(id = "v9"))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.playVideo(
            SearchHit.Video(
                title = entry.title,
                subtitle = null,
                artworkUrl = null,
                watchUrl = entry.watchUrl,
                durationSeconds = 90,
            ),
        )
        advanceUntilIdle()

        val playing = playback.state.value
        assertNotNull(playing)
        assertEquals("Sample video", playing?.title)
        assertEquals(false, viewModel.uiState.value.resolveFailed)
    }

    @Test
    fun `unresolvable video surfaces a failure flag`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.playVideo(
            SearchHit.Video(
                title = "Gone",
                subtitle = null,
                artworkUrl = null,
                watchUrl = HttpUrl.of("https://example.com/watch?v=gone"),
                durationSeconds = null,
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.resolveFailed)
    }
}
