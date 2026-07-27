package com.dewijones92.totum.ui.library

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.download.fake.FakeDownloadManager
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.asPlayable
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val downloads = FakeDownloadManager()
    private lateinit var queue: PlaybackQueue

    private val watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=abc123")

    private fun item(id: String, url: HttpUrl?) = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId("src"),
        title = id,
        publishedAt = null,
        duration = null,
        mediaUrl = url,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val controller = FakePlaybackController()
        queue = PlaybackQueue(
            controller,
            VideoPlaybackLauncher(
                VideoResolver(FakeYtDlpEngine(), SkipSegmentSource { emptyList() }),
                controller,
                FakeYouTubeWatchHistory(),
                InMemoryPlayHistoryStore(),
            ),
            kotlinx.coroutines.CoroutineScope(dispatcher),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Subscribes as the screen does. `downloaded` is `WhileSubscribed`, so with no
     * collector it never leaves its initial empty value.
     */
    // A fixed size, so the test is about listing rather than the filesystem.
    private fun TestScope.viewModel() = LibraryViewModel(
        queue,
        downloads,
        fileSize = { FAKE_SIZE },
        io = dispatcher
    ).also { model ->
        // Both are WhileSubscribed, so both need a collector or they stay at their
        // initial value — which reads exactly like the feature not working.
        backgroundScope.launch { model.downloaded.collect { } }
        backgroundScope.launch { model.storage.collect { } }
    }

    /**
     * The regression this replaces: Library combined podcast episodes with download
     * states, so a downloaded video was on disk and simply never listed.
     */
    @Test
    fun `lists downloads from both pillars`() = runTest(dispatcher) {
        val video = PlayableItem(item("vid", null), PlayHandle.Video(watchUrl))
        val episode = item("ep", HttpUrl.of("https://cdn.example.com/ep.mp3")).asPlayable()
        downloads.download(video, audioOnly = true)
        downloads.download(episode)

        val model = viewModel()
        advanceUntilIdle()
        val byId = model.downloaded.value.associate { it.item.id.value to it.media.pillar }

        assertEquals(mapOf("vid" to MediaKind.VIDEO, "ep" to MediaKind.PODCAST), byId)
    }

    @Test
    fun `playing a download starts the local file, not the network`() = runTest(dispatcher) {
        val video = PlayableItem(item("vid", null), PlayHandle.Video(watchUrl))
        downloads.download(video, audioOnly = false)

        val model = viewModel()
        advanceUntilIdle()
        model.play(model.downloaded.value.single())
        advanceUntilIdle()

        val queued = queue.state.value.entries.single().item
        assertEquals(PlayHandle.LocalVideo("/fake/vid.media"), queued.handle)
    }

    @Test
    fun `deleting a download removes it from the list`() = runTest(dispatcher) {
        downloads.download(item("ep", HttpUrl.of("https://cdn.example.com/ep.mp3")))
        val model = viewModel()
        advanceUntilIdle()

        model.delete(model.downloaded.value.single())
        advanceUntilIdle()

        assertEquals(emptyList<String>(), model.downloaded.value.map { it.item.id.value })
    }

    /** The point of the summary: it must agree with the rows, not be counted separately. */
    @Test
    fun `storage totals what the rows show`() = runTest(dispatcher) {
        downloads.download(item("a", HttpUrl.of("https://cdn.example.com/a.mp3")))
        downloads.download(item("b", HttpUrl.of("https://cdn.example.com/b.mp3")))
        val model = viewModel()
        advanceUntilIdle()

        val storage = model.storage.value
        assertEquals(2, storage.itemCount)
        assertEquals(FAKE_SIZE * 2, storage.usedBytes)
        assertEquals(model.downloaded.value.sumOf { it.sizeBytes }, storage.usedBytes)
    }

    private companion object {
        const val FAKE_SIZE = 1_500_000L
    }
}
