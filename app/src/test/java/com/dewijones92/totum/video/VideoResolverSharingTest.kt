package com.dewijones92.totum.video

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.sponsorblock.SkipSegmentSource
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.ytdlp.ExtractionResult
import com.dewijones92.totum.ytdlp.MediaFormat
import com.dewijones92.totum.ytdlp.MediaMetadata
import com.dewijones92.totum.ytdlp.YtDlpEngine
import com.dewijones92.totum.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * One extraction, however many callers want it at once.
 *
 * A real report (0.1.226) showed the same video resolved twice, overlapping — the second
 * caller arrived while the first was still extracting, saw an empty cache and did the whole
 * thing again. At 10-20s per extraction with the JS runtime, that is a minute of duplicated
 * work for one video.
 */
class VideoResolverSharingTest {

    private val url = HttpUrl.of("https://www.youtube.com/watch?v=abc")
    private val source = SourceId("s")

    /** Counts extractions and takes its time, so callers genuinely overlap. */
    private class SlowEngine(private val calls: AtomicInteger) : YtDlpEngine by FakeYtDlpEngine() {
        override suspend fun extract(url: HttpUrl): ExtractionResult {
            calls.incrementAndGet()
            delay(1_000)
            return ExtractionResult.Success(
                MediaMetadata(
                    id = "abc",
                    title = "A video",
                    uploader = null,
                    durationSeconds = 10,
                    thumbnailUrl = null,
                    formats = listOf(
                        MediaFormat("18", "mp4", 640, 360, true, true, null, "https://x.test/v", "avc1", "mp4a"),
                    ),
                ),
            )
        }
    }

    private fun resolver(calls: AtomicInteger) =
        VideoResolver(SlowEngine(calls), SkipSegmentSource { emptyList() })

    @Test
    fun `two callers at once share one extraction`() = runTest {
        val calls = AtomicInteger()
        val resolver = resolver(calls)

        val first = async { resolver.resolve(url, source, asked = "play") }
        val second = async { resolver.resolve(url, source, asked = "prefetch") }

        assertSame("both should get the same resolved object", first.await(), second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun `a caller arriving after it finished is served from cache, still one extraction`() = runTest {
        val calls = AtomicInteger()
        val resolver = resolver(calls)

        resolver.resolve(url, source)
        resolver.resolve(url, source)

        assertEquals(1, calls.get())
    }

    @Test
    fun `different videos are not conflated`() = runTest {
        val calls = AtomicInteger()
        val resolver = resolver(calls)
        val other = HttpUrl.of("https://www.youtube.com/watch?v=xyz")

        val a = async { resolver.resolve(url, source) }
        val b = async { resolver.resolve(other, source) }
        a.await()
        b.await()

        assertEquals(2, calls.get())
    }
}
