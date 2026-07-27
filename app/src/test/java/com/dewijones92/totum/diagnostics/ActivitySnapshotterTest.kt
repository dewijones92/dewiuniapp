package com.dewijones92.totum.diagnostics

import com.dewijones92.totum.common.Breadcrumbs
import com.dewijones92.totum.data.download.fake.FakeDownloadManager
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.playback.fake.FakePlaybackController
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.video.VideoPlaybackLauncher
import com.dewijones92.totum.video.VideoResolver
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitySnapshotterTest {

    private val downloads = FakeDownloadManager()
    private val controller = FakePlaybackController()

    @Before
    fun reset() = Breadcrumbs.clear()

    @After
    fun tidy() = Breadcrumbs.clear()

    private fun snapshots() = Breadcrumbs.snapshot().filter { it.tag == "snapshot" }

    /** An idle app in the background must not spend the retention window on nothing. */
    @Test
    fun `an idle app records nothing`() = runTest {
        val queue = PlaybackQueue(controller, launcher(), backgroundScope)
        ActivitySnapshotter(controller, downloads, queue, backgroundScope, INTERVAL).start()

        advanceTimeBy(INTERVAL * 5)

        assertEquals(emptyList<String>(), snapshots().map { it.message })
    }

    /**
     * The point of the whole thing: a download in flight produces no transitions, so
     * without a periodic sample it is invisible for as long as it takes.
     */
    @Test
    fun `a download in flight is recorded even with nothing playing`() = runTest {
        val queue = PlaybackQueue(controller, launcher(), backgroundScope)
        downloads.setDownloading(MediaItemId("ep-1"), downloadedBytes = 4_000_000, totalBytes = 10_000_000)
        ActivitySnapshotter(controller, downloads, queue, backgroundScope, INTERVAL).start()

        advanceTimeBy(INTERVAL + 1)

        val line = snapshots().firstOrNull()?.message
        assertTrue("expected a snapshot naming the download, got: $line", line?.contains("downloading=1") == true)
        assertTrue("expected its progress, got: $line", line?.contains("40%") == true)
    }

    private fun launcher() = VideoPlaybackLauncher(
        VideoResolver(FakeYtDlpEngine(), SkipSegmentSource { emptyList() }),
        controller,
        FakeYouTubeWatchHistory(),
        InMemoryPlayHistoryStore(),
    )

    private companion object {
        const val INTERVAL = 1_000L
    }
}
