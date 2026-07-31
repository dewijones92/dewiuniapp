package com.dewijones92.totum.sabr

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning a SABR conversation into bytes in order.
 *
 * The shapes here are the real ones: a response interleaves formats (no track bitfield returns
 * video without audio), `MEDIA` payloads carry a one-byte prefix that is not media, and progress
 * comes from `player_time_ms` rather than from anything we tell it about bytes.
 */
class SabrStreamTest {

    private val audio = SabrFormat(itag = 251, lastModified = 1L, xtags = "orig")
    private val video = SabrFormat(itag = 137, lastModified = 2L)

    /** Builds a UMP response the way YouTube frames one, ids and all. */
    private fun response(vararg runs: Triple<SabrFormat, Long, ByteArray>): ByteArray {
        var out = ByteArray(0)
        runs.forEachIndexed { id, (format, offset, payload) ->
            out += header(id, format, offset, payload.size)
            out += media(id, payload)
        }
        return out
    }

    private fun header(id: Int, format: SabrFormat, offset: Long, length: Int) = umpPart(
        UmpPart.MEDIA_HEADER,
        Protobuf.number(1, id.toLong()) +
            Protobuf.number(3, format.itag.toLong()) +
            Protobuf.number(6, offset) +
            Protobuf.number(14, length.toLong()),
    )

    /** A MEDIA part names its own run: the payload begins with the header id. */
    private fun media(id: Int, payload: ByteArray) =
        umpPart(UmpPart.MEDIA, byteArrayOf(id.toByte()) + payload)

    private fun umpPart(type: Int, payload: ByteArray): ByteArray {
        fun varint(value: Int) = if (value < 0x80) {
            byteArrayOf(value.toByte())
        } else {
            byteArrayOf((0x80 or (value and 0x3F)).toByte(), (value shr 6).toByte())
        }
        return varint(type) + varint(payload.size) + payload
    }

    private class Fake(private val responses: List<ByteArray>) : SabrTransport {
        val bodies = mutableListOf<ByteArray>()
        override suspend fun post(url: String, body: ByteArray): ByteArray {
            bodies += body
            return responses.getOrElse(bodies.size - 1) { ByteArray(0) }
        }
    }

    private fun stream(transport: SabrTransport, format: SabrFormat = audio) = SabrStream(
        url = "https://example.test/videoplayback",
        ustreamerConfig = byteArrayOf(1, 2, 3),
        format = format,
        kind = if (format == audio) SabrTrackKind.AUDIO else SabrTrackKind.VIDEO,
        transport = transport,
    )

    @Test
    fun `serves the requested format's bytes from the start`() = runTest {
        val fake = Fake(listOf(response(Triple(audio, 0L, byteArrayOf(10, 11, 12)))))

        val bytes = stream(fake).read(from = 0)

        assertArrayEquals(byteArrayOf(10, 11, 12), bytes)
    }

    /**
     * The interleaving that makes this necessary: a video request returns audio too, and the
     * wrong bytes spliced into a track decode as corruption rather than failing.
     */
    @Test
    fun `bytes belonging to another format are dropped`() = runTest {
        val fake = Fake(
            listOf(
                response(
                    Triple(audio, 0L, byteArrayOf(1, 2)),
                    Triple(video, 0L, byteArrayOf(9, 9, 9, 9)),
                ),
            ),
        )

        assertArrayEquals(byteArrayOf(9, 9, 9, 9), stream(fake, video).read(from = 0))
    }

    @Test
    fun `the content length is taken from the header of our own format`() = runTest {
        val fake = Fake(listOf(response(Triple(audio, 0L, ByteArray(5)))))
        val stream = stream(fake)

        stream.read(from = 0)

        assertEquals(5L, stream.contentLength)
    }

    /**
     * Progress is driven by `player_time_ms` because that is what the server responds to —
     * `buffered_ranges` alone advanced twice and then stalled. Each fetch must therefore ask
     * from further on than the last, or the same bytes come back forever.
     */
    @Test
    fun `each fetch asks from a later player time`() = runTest {
        val fake = Fake(
            listOf(
                response(Triple(audio, 0L, byteArrayOf(1))),
                response(Triple(audio, 1L, byteArrayOf(2))),
            ),
        )
        val stream = stream(fake)

        stream.read(from = 0)
        stream.read(from = 1)

        val times = fake.bodies.map { body ->
            val state = Protobuf.read(body)[1]?.first() as Protobuf.Value.Bytes
            (Protobuf.read(state.value)[28]?.first() as Protobuf.Value.Number).value
        }
        assertEquals(listOf(0L, 10_000L), times)
    }

    @Test
    fun `an audio stream asks for audio alone, which is a tenth of the bytes`() = runTest {
        val fake = Fake(listOf(response(Triple(audio, 0L, byteArrayOf(1)))))

        stream(fake).read(from = 0)

        val state = Protobuf.read(fake.bodies.single())[1]?.first() as Protobuf.Value.Bytes
        assertEquals(1L, (Protobuf.read(state.value)[40]!!.first() as Protobuf.Value.Number).value)
        assertTrue("audio goes in field 16", Protobuf.read(fake.bodies.single())[16] != null)
    }

    @Test
    fun `a video stream asks in field 17 and accepts audio alongside`() = runTest {
        val fake = Fake(listOf(response(Triple(video, 0L, byteArrayOf(1)))))

        stream(fake, video).read(from = 0)

        val body = fake.bodies.single()
        assertTrue("video goes in field 17", Protobuf.read(body)[17] != null)
        val state = Protobuf.read(body)[1]?.first() as Protobuf.Value.Bytes
        assertEquals(
            "0 means both tracks",
            0L,
            (Protobuf.read(state.value)[40]!!.first() as Protobuf.Value.Number).value
        )
    }

    /**
     * The bug that made video decode to corruption, reproduced.
     *
     * Runs INTERLEAVE. Measured on itag 134 with audio alongside it, one real response went
     * `MEDIA_HEADER(3), MEDIA(3), MEDIA(1), MEDIA(1), MEDIA_END(1), MEDIA_HEADER(4), MEDIA(4),
     * MEDIA(3)` — header 1's run resuming three parts after header 3 was declared. Binding a
     * MEDIA part to the most recent header therefore splices one format's bytes into another's
     * at the wrong offset, and ExoPlayer reports `Invalid NAL length` rather than failing
     * cleanly. Audio-only hid it, because one format's runs arrive in order.
     */
    @Test
    fun `interleaved runs go to the format that owns them, not the last header seen`() = runTest {
        // header 0 = our video at 0; header 1 = audio at 0; then MORE of header 0, out of order.
        val body = header(0, video, 0, 4) + media(0, byteArrayOf(1, 2)) +
            header(1, audio, 0, 2) + media(1, byteArrayOf(9, 9)) +
            media(0, byteArrayOf(3, 4))
        val fake = Fake(listOf(body))

        val first = stream(fake, video).read(from = 0)

        // Both of header 0's runs, contiguous, with the audio bytes nowhere among them.
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), first)
    }

    /** A MEDIA part naming a run we never saw declared must be dropped, not guessed at. */
    @Test
    fun `media for an unknown header id is ignored`() = runTest {
        val body = header(0, video, 0, 2) + media(0, byteArrayOf(7, 7)) + media(42, byteArrayOf(1, 1))
        val fake = Fake(listOf(body))

        assertArrayEquals(byteArrayOf(7, 7), stream(fake, video).read(from = 0))
    }

    /** A server with nothing left to send must end the stream, not spin forever. */
    @Test
    fun `an empty response ends the stream rather than looping`() = runTest {
        val fake = Fake(emptyList())

        assertEquals(0, stream(fake).read(from = 0).size)
        assertTrue("must give up, not retry indefinitely", fake.bodies.size <= 6)
    }
}
