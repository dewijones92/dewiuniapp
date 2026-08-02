package com.dewijones92.totum.video

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.innertube.player.PlayableFormat
import com.dewijones92.totum.innertube.player.PlayerDetails
import com.dewijones92.totum.innertube.player.PlayerResult
import com.dewijones92.totum.innertube.player.StreamingData
import com.dewijones92.totum.ytdlp.ExtractionResult
import com.dewijones92.totum.ytdlp.MediaFormat
import com.dewijones92.totum.ytdlp.MediaMetadata
import com.dewijones92.totum.ytdlp.YtDlpEngine
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * YouTube's player response is asked BEFORE yt-dlp, and extraction still catches everything
 * it cannot answer.
 *
 * The reordering is the point: extraction measured 13.9s on Dewi's phone and 23.4s on the
 * emulator against ~0.2s for the player call, and that gap is the entire wait before a video
 * starts. The risk is equally the point — extraction has years of edge cases behind it, so
 * every way the fast path can come up empty must fall through rather than fail.
 */
class VideoResolverFastPathTest {

    /** A REAL-shaped id: youTubeVideoId() requires exactly 11 characters and returns null
     *  otherwise, so a short placeholder silently disables the whole fast path. */
    private val videoId = VIDEO_ID
    private val url = HttpUrl.of("https://www.youtube.com/watch?v=$VIDEO_ID")
    private val source = SourceId("s")

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
    }

    /** Counts extractions, so a test can prove one did NOT happen. */
    private class CountingEngine(val calls: AtomicInteger) : YtDlpEngine by FakeYtDlpEngine() {
        override suspend fun extract(url: HttpUrl): ExtractionResult {
            calls.incrementAndGet()
            return ExtractionResult.Success(
                MediaMetadata(
                    id = VIDEO_ID,
                    title = "From extraction",
                    uploader = null,
                    durationSeconds = 10,
                    thumbnailUrl = null,
                    formats = listOf(
                        MediaFormat(
                            "18", "mp4", 640, 360, true, true, null,
                            "https://x.test/extracted", "avc1", "mp4a",
                        ),
                    ),
                ),
            )
        }
    }

    private fun playerResponse(url: String?) = PlayerResult.Success(
        streaming = StreamingData(
            formats = listOfNotNull(
                url?.let {
                    PlayableFormat(
                        itag = 18,
                        mimeType = "video/mp4; codecs=\"avc1, mp4a\"",
                        height = 360,
                        bitrate = 1_000,
                        url = HttpUrl.of(it),
                    )
                },
            ),
        ),
        details = PlayerDetails(
            videoId = VIDEO_ID,
            title = "From the player",
            author = null,
            channelId = null,
            lengthSeconds = 10,
            thumbnailUrl = null,
            description = null,
        ),
    )

    private fun resolver(calls: AtomicInteger, streams: PlayerStreams?) =
        VideoResolver(
            engine = CountingEngine(calls),
            skipSegments = SkipSegmentSource { emptyList() },
            playerStreams = streams,
        )

    @Test
    fun `the player response is used and yt-dlp is never asked`() = runTest {
        val calls = AtomicInteger()
        val resolved = resolver(calls) { playerResponse("https://x.test/fast") }
            .resolve(url, source, asked = "play")

        assertEquals("From the player", resolved?.item?.title)
        assertEquals(0, calls.get())
    }

    /**
     * The whole safety argument for reordering. A refusal, an unsolvable `n` and a SABR-only
     * response all surface here as null, and each must reach extraction — otherwise a video
     * that plays today stops playing because it was asked in a different order.
     */
    @Test
    fun `a player response that yields nothing falls through to extraction`() = runTest {
        val calls = AtomicInteger()
        val resolved = resolver(calls) { null }.resolve(url, source, asked = "play")

        assertEquals("From extraction", resolved?.item?.title)
        assertEquals(1, calls.get())
    }

    /** A response whose formats all lack URLs is no more playable than no response at all. */
    @Test
    fun `a player response with no fetchable url falls through to extraction`() = runTest {
        val calls = AtomicInteger()
        val resolved = resolver(calls) { playerResponse(url = null) }
            .resolve(url, source, asked = "play")

        assertEquals("From extraction", resolved?.item?.title)
        assertEquals(1, calls.get())
    }

    /**
     * A throwing player must not take the resolve down with it — the fast path is an
     * optimisation, and an optimisation that can break playback is not one.
     */
    @Test
    fun `a player that throws falls through to extraction`() = runTest {
        val calls = AtomicInteger()
        val resolved = resolver(calls) { error("innertube exploded") }
            .resolve(url, source, asked = "play")

        assertEquals("From extraction", resolved?.item?.title)
        assertEquals(1, calls.get())
    }

    /** With no player wired at all — tests, previews, a signed-out build — nothing changes. */
    @Test
    fun `no player configured means extraction as before`() = runTest {
        val calls = AtomicInteger()
        val resolved = resolver(calls, streams = null).resolve(url, source, asked = "play")

        assertEquals("From extraction", resolved?.item?.title)
        assertEquals(1, calls.get())
    }

    /** A non-YouTube URL has no video id to ask about, so it must not even try. */
    @Test
    fun `a non-youtube url goes straight to extraction`() = runTest {
        val calls = AtomicInteger()
        val resolved = resolver(calls) { playerResponse("https://x.test/fast") }
            .resolve(HttpUrl.of("https://example.test/media.mp4"), source, asked = "play")

        // The player has no video id to be asked about, so extraction is the only thing that
        // could have produced this.
        assertEquals("From extraction", resolved?.item?.title)
        assertEquals(1, calls.get())
        assertTrue(resolved != null)
    }

    /** The fast result is cached like any other, so a second ask costs nothing at all. */
    @Test
    fun `a fast resolve is cached`() = runTest {
        val calls = AtomicInteger()
        val players = AtomicInteger()
        val resolver = resolver(calls) {
            players.incrementAndGet()
            playerResponse("https://x.test/fast")
        }

        resolver.resolve(url, source, asked = "play")
        resolver.resolve(url, source, asked = "play")

        assertEquals(1, players.get())
        assertEquals(0, calls.get())
    }

    /**
     * The safety valve for the reordering, and the reason it is safe to ship.
     *
     * The player response is right for most videos and wrong for some: measured on the emulator
     * 2026-08-02, several played from it while others 403'd on open where extraction handled
     * them fine. Recovery re-resolves after a dead stream, so without this it would take the
     * same bad path three times and skip the video. One failure is enough to switch it.
     */
    @Test
    fun `a video whose fast stream failed extracts from then on`() = runTest {
        val calls = AtomicInteger()
        val resolver = resolver(calls) { playerResponse("https://x.test/fast") }

        assertEquals("From the player", resolver.resolve(url, source, asked = "play")?.item?.title)
        assertEquals(0, calls.get())

        // What recovery does when the stream will not open.
        resolver.forget(url)

        assertEquals("From extraction", resolver.resolve(url, source, asked = "play")?.item?.title)
        assertEquals(1, calls.get())
    }

    /** Forgetting a video the fast path never produced must not blame it. */
    @Test
    fun `forgetting an extracted video does not disable the fast path for it`() = runTest {
        val calls = AtomicInteger()
        val resolver = resolver(calls) { null }

        assertEquals("From extraction", resolver.resolve(url, source, asked = "play")?.item?.title)
        resolver.forget(url)

        // The player is offered again — it was never the thing that failed.
        var asked = false
        val second = VideoResolver(
            engine = CountingEngine(calls),
            skipSegments = SkipSegmentSource { emptyList() },
            playerStreams = {
                asked = true
                playerResponse("https://x.test/fast")
            },
        )
        second.forget(url)
        assertEquals("From the player", second.resolve(url, source, asked = "play")?.item?.title)
        assertEquals(true, asked)
    }

    /** Nothing anywhere is still null, not an exception and not a half-built item. */
    @Test
    fun `neither path producing anything is a null`() = runTest {
        val resolver = VideoResolver(
            engine = object : YtDlpEngine by FakeYtDlpEngine() {
                override suspend fun extract(url: HttpUrl) =
                    ExtractionResult.Failure.Extractor("nope")
            },
            skipSegments = SkipSegmentSource { emptyList() },
            playerStreams = { null },
        )

        assertNull(resolver.resolve(url, source, asked = "play"))
    }
}
