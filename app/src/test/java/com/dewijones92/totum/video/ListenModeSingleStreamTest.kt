package com.dewijones92.totum.video

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.history.fake.InMemoryPlayHistoryStore
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.innertube.history.fake.FakeYouTubeWatchHistory
import com.dewijones92.totum.playback.fake.FakePlaybackController
import com.dewijones92.totum.ytdlp.ExtractionResult
import com.dewijones92.totum.ytdlp.MediaFormat
import com.dewijones92.totum.ytdlp.MediaMetadata
import com.dewijones92.totum.ytdlp.YtDlpEngine
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Switching between Listen and Watch must not restart a single-stream item.
 *
 * Dewi, 2026-08-02: *"listen mode is a bit weird with torrents??"*. A torrent is one file
 * carrying both tracks, so there is no audio stream to switch to and no quality ladder to move
 * within — but Watch re-prepared the media anyway. Report 0.1.317 caught it: toggling the mode
 * on a Peep Show episode restarted it at 20ms from 5876ms, repeatedly, and the video decoded on
 * regardless because there was only ever the one stream.
 */
class ListenModeSingleStreamTest {

    private val playback = FakePlaybackController()

    /** One stream, both tracks — a torrent, or a podcast enclosure. */
    private class SingleStreamEngine : YtDlpEngine by FakeYtDlpEngine() {
        override suspend fun extract(url: HttpUrl) = ExtractionResult.Success(
            MediaMetadata(
                id = "one-stream",
                title = "S01E01",
                uploader = null,
                durationSeconds = 1_400,
                thumbnailUrl = null,
                // Muxed, and the ONLY format: no audio-only sibling to switch to.
                formats = listOf(
                    MediaFormat(
                        "0", "mkv", null, null, hasVideo = true, hasAudio = true,
                        fileSizeBytes = null, url = "https://home.test/ts/stream/S01E01.mkv",
                        videoCodec = "hvc1", audioCodec = "mp4a",
                    ),
                ),
            ),
        )
    }

    private fun launcher() = VideoPlaybackLauncher(
        resolver = VideoResolver(SingleStreamEngine(), SkipSegmentSource { emptyList() }),
        playback = playback,
        watchHistory = FakeYouTubeWatchHistory(),
        playHistory = InMemoryPlayHistoryStore(),
    )

    private val watchUrl = HttpUrl.of("https://home.test/ts/stream/S01E01.mkv")

    /**
     * The listing that was tapped, which is now what `play` takes.
     *
     * It takes the item rather than a bare URL because a resolution has nothing to say about view
     * counts or publication dates, and building a fresh item from one dropped both — see
     * `MediaItem.withStreamFrom`.
     */
    private val listing = MediaItem(
        id = MediaItemId("one-stream"),
        sourceId = SourceId("torrent"),
        title = "S01E01",
        publishedAt = null,
        duration = null,
        mediaUrl = watchUrl,
    )

    @Test
    fun `switching to watch does not re-prepare a single-stream item`() = runTest {
        val launcher = launcher()
        launcher.play(listing, watchUrl)
        val afterPlay = playback.played.size

        launcher.watch()

        assertEquals("watch must not replay a one-stream item", afterPlay, playback.played.size)
    }

    /** Listen has nothing to switch to either, and must leave playback exactly alone. */
    @Test
    fun `switching to listen does not re-prepare a single-stream item`() = runTest {
        val launcher = launcher()
        launcher.play(listing, watchUrl)
        val afterPlay = playback.played.size

        launcher.listen()

        assertEquals("listen must not replay a one-stream item", afterPlay, playback.played.size)
    }
}
