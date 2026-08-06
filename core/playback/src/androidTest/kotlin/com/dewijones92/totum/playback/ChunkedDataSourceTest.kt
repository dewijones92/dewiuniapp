package com.dewijones92.totum.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * The real [ChunkedDataSource], over an upstream that behaves the way googlevideo does.
 *
 * [ChunkedReadTest] holds the arithmetic; this holds the wiring, which is the half that shipped
 * broken. Instrumented only because a [DataSpec] needs an `android.net.Uri` — there is no device
 * behaviour here and no network, so it is deterministic and runs on every commit.
 *
 * The case that matters is a stream **resumed partway through**, and that is not an edge case:
 * ExoPlayer restarts its loader at a non-zero byte offset on every seek and every time the load
 * control pauses loading, so it is how nearly every read of a long video begins. Report 0.1.359
 * (2026-08-06) had four consecutive videos stall inside their last 45 seconds and never recover,
 * because the source took `clen` — the length of the WHOLE resource — as the bytes remaining from
 * wherever it had started.
 *
 * No apostrophes or commas in the test names: dex cannot represent either in a method name and D8
 * fails the whole androidTest build with "cannot be represented in dex format".
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ChunkedDataSourceTest {

    /** THE BUG. A resumed stream must serve what is left of it, and stop there. */
    @Test
    fun `a stream resumed partway through serves its tail and then ends`() {
        val server = RangeServer(content(size = 10_000))
        val source = ChunkedDataSource(server, CHUNK)

        val declared = source.open(server.spec(position = 7_000))
        assertEquals("open must report what is left from here, not the whole resource", 3_000, declared)

        val read = source.drain()
        assertEquals(3_000, read.size)
        assertTrue("the wrong bytes came back", read.contentEquals(server.content.copyOfRange(7_000, 10_000)))
        server.assertNothingAskedPastTheEnd()
    }

    @Test
    fun `a stream read from the start serves all of it`() {
        val server = RangeServer(content(size = 10_000))
        val source = ChunkedDataSource(server, CHUNK)

        assertEquals(10_000, source.open(server.spec(position = 0)))
        assertTrue(source.drain().contentEquals(server.content))
        server.assertNothingAskedPastTheEnd()
    }

    /** Small chunks, so there are many ranges for the offsets to drift in. */
    @Test
    fun `the ranges of a resumed stream are contiguous and inside the resource`() {
        val server = RangeServer(content(size = 10_000))
        val source = ChunkedDataSource(server, chunkBytes = 512)

        source.open(server.spec(position = 4_096))
        source.drain()

        var expected = 4_096L
        server.asked.forEach { range ->
            assertEquals("a gap or an overlap between ranges: ${server.asked}", expected, range.first)
            expected = range.last + 1
        }
        assertEquals("the ranges must finish exactly at the end of the resource", 10_000L, expected)
    }

    /**
     * A URL claiming more bytes than the server holds — an expired or rewritten stream.
     *
     * Indistinguishable from the tail bug from the outside, and it must end rather than be asked
     * again forever.
     */
    @Test
    fun `a resource shorter than it claims ends instead of spinning`() {
        val server = RangeServer(content(size = 5_000), claims = 20_000)
        val source = ChunkedDataSource(server, CHUNK)

        source.open(server.spec(position = 0))
        assertEquals("it must stop at what actually exists", 5_000, source.drain().size)
    }

    /** No `clen` in the URL: the length comes from a probe, which already answers from the position. */
    @Test
    fun `a url that states no length is probed and still serves only its tail`() {
        val server = RangeServer(content(size = 10_000), claims = null)
        val source = ChunkedDataSource(server, CHUNK)

        source.open(server.spec(position = 6_000))
        assertEquals(4_000, source.drain().size)
        server.assertNothingAskedPastTheEnd()
    }

    /** A caller that states its own length is served exactly that, from where it asked. */
    @Test
    fun `a caller that states a length gets precisely that many bytes`() {
        val server = RangeServer(content(size = 10_000))
        val source = ChunkedDataSource(server, CHUNK)

        assertEquals(1_500, source.open(server.spec(position = 2_000, length = 1_500)))
        val read = source.drain()
        assertEquals(1_500, read.size)
        assertTrue(read.contentEquals(server.content.copyOfRange(2_000, 3_500)))
    }

    /**
     * A stand-in for googlevideo: bounded ranges are honoured, and a range starting at or past the
     * end of the content answers with **no bytes** rather than an error.
     *
     * That empty answer is the shape that hurt. An error would have surfaced as a load error and
     * been visible in a report; nothing at all meant the read went round again for the same bytes
     * inside a single `read()` call — so the load never completed, never cancelled and never errored,
     * and everything it held was never released. The report shows exactly that: loads outstanding
     * climbing 35 → 37 with the oldest frozen, 17 completions for 53MB, `loadErrors` static at 14.
     */
    private class RangeServer(
        val content: ByteArray,
        /** What the URL says the resource's length is; null states nothing, as some URLs do not. */
        private val claims: Int? = content.size,
    ) : DataSource {

        /** Every range asked for, so a request past the end of the resource is provable. */
        val asked = mutableListOf<LongRange>()

        private var cursor = 0
        private var end = 0

        private val uri = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=399" +
            claims?.let { "&clen=$it" }.orEmpty()

        fun spec(position: Long, length: Long = C.LENGTH_UNSET.toLong()): DataSpec =
            DataSpec.Builder().setUri(uri).setPosition(position).setLength(length).build()

        fun assertNothingAskedPastTheEnd() = assertTrue(
            "a range was asked for past the end of the ${content.size}-byte resource: $asked",
            asked.none { it.last >= content.size },
        )

        override fun open(dataSpec: DataSpec): Long {
            val from = dataSpec.position.toInt()
            val want = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                content.size - from
            } else {
                dataSpec.length.toInt()
            }
            asked += from.toLong() until (from + want).toLong().coerceAtLeast(from.toLong())
            cursor = from.coerceIn(0, content.size)
            end = (from + want).coerceIn(cursor, content.size)
            return (end - cursor).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (cursor >= end) return C.RESULT_END_OF_INPUT
            val n = minOf(length, end - cursor)
            content.copyInto(buffer, offset, cursor, cursor + n)
            cursor += n
            return n
        }

        override fun close() = Unit
        override fun getUri() = null
        override fun addTransferListener(transferListener: TransferListener) = Unit
    }

    /** Reads a source to end-of-input, with a hard cap so a source that will not end fails fast. */
    private fun DataSource.drain(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER)
        var reads = 0
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) return out.toByteArray()
            out.write(buffer, 0, n)
            assertTrue(
                "the source never reached end-of-input after $reads reads — this is the hang, " +
                    "and before the fix it had no bound at all",
                ++reads < MAX_READS,
            )
        }
    }

    private fun content(size: Int) = ByteArray(size) { (it % BYTE_RANGE).toByte() }

    private companion object {
        const val CHUNK = 1_024L

        /** Not a round number and not a multiple of the chunk, so a boundary bug has to show. */
        const val READ_BUFFER = 337
        const val BYTE_RANGE = 251

        /** Far more than any of these reads needs, and finite — which the old loop was not. */
        const val MAX_READS = 10_000
    }
}
