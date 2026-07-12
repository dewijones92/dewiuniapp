package com.dewijones92.uniapp.ui.videos

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ui.videos.VideosViewModel.CheckState
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class VideosViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeYtDlpEngine()
    private lateinit var viewModel: VideosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = VideosViewModel(engine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid url is rejected before reaching the engine`() = runTest(dispatcher) {
        viewModel.check("not a url")
        advanceUntilIdle()
        assertEquals(CheckState.Error.InvalidUrl, viewModel.checkState.value)
    }

    @Test
    fun `known url lands in Found with metadata`() = runTest(dispatcher) {
        val url = HttpUrl.of("https://example.com/watch?v=known")
        val metadata = FakeYtDlpEngine.sampleMetadata()
        engine.registerMedia(url, metadata)

        viewModel.check(url.value)
        advanceUntilIdle()

        assertEquals(CheckState.Found(metadata), viewModel.checkState.value)
    }

    @Test
    fun `unknown url surfaces Unsupported`() = runTest(dispatcher) {
        viewModel.check("https://example.com/unknown")
        advanceUntilIdle()
        assertEquals(CheckState.Error.Unsupported, viewModel.checkState.value)
    }

    @Test
    fun `reset returns to Idle`() = runTest(dispatcher) {
        viewModel.check("not a url")
        advanceUntilIdle()
        viewModel.reset()
        assertEquals(CheckState.Idle, viewModel.checkState.value)
    }
}
