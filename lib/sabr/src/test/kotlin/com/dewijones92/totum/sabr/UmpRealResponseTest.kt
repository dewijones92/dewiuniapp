package com.dewijones92.totum.sabr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The framing of a REAL SABR response, replayed.
 *
 * Captured 2026-07-31 from a live POST to `serverAbrStreamingUrl` carrying nothing but
 * `videoPlaybackUstreamerConfig` — 212246 bytes, 26 parts. The part types and sizes are the
 * genuine article; the payload bytes are synthetic, because the real ones are somebody's
 * copyrighted video and none of them are needed to prove the framing reads correctly.
 *
 * Worth having as well as the hand-built vectors: this is the shape that actually turns up —
 * multi-part media runs, 32769-byte MEDIA parts, a MEDIA_HEADER arriving mid-run, and sizes
 * that cross every varint width boundary.
 */
class UmpRealResponseTest {

    /** type to payload size, in the order YouTube sent them. */
    private val realSequence = listOf(
        51 to 14550, 47 to 52, 58 to 2, 42 to 130, 35 to 128, 49 to 0,
        20 to 147, 21 to 2117, 22 to 1, 42 to 92, 20 to 52, 21 to 3229,
        22 to 1, 20 to 161, 21 to 32769, 20 to 66, 21 to 32769, 21 to 13291,
        22 to 1, 20 to 69, 21 to 32769, 21 to 32046, 22 to 1, 21 to 32769,
        21 to 14962, 22 to 1,
    )

    /** Encodes a UMP varint the way YouTube does — narrowest width that fits. */
    private fun varint(value: Int): ByteArray = when {
        value < 0x80 -> byteArrayOf(value.toByte())
        value < (1 shl 14) -> byteArrayOf(
            (0x80 or (value and 0x3F)).toByte(),
            (value shr 6).toByte(),
        )
        value < (1 shl 21) -> byteArrayOf(
            (0xC0 or (value and 0x1F)).toByte(),
            ((value shr 5) and 0xFF).toByte(),
            (value shr 13).toByte(),
        )
        else -> byteArrayOf(
            (0xE0 or (value and 0x0F)).toByte(),
            ((value shr 4) and 0xFF).toByte(),
            ((value shr 12) and 0xFF).toByte(),
            (value shr 20).toByte(),
        )
    }

    private fun body(): ByteArray {
        val out = ArrayList<Byte>()
        realSequence.forEach { (type, size) ->
            out += varint(type).toList()
            out += varint(size).toList()
            // A distinctive first byte per part, so a mis-split shows up as wrong content
            // rather than merely wrong length.
            repeat(size) { index -> out += (if (index == 0) type else index and 0xFF).toByte() }
        }
        return out.toByteArray()
    }

    @Test
    fun `every part of a real response is recovered, in order and at the right size`() {
        val raw = body()

        val result = UmpReader.read(raw)

        assertEquals(realSequence.size, result.parts.size)
        assertEquals(realSequence.map { it.first }, result.parts.map { it.type })
        assertEquals(realSequence.map { it.second }, result.parts.map { it.payload.size })
        assertEquals("all of it must be consumed", raw.size, result.consumed)
    }

    @Test
    fun `the media parts carry the bytes a player would feed to the decoder`() {
        val media = UmpReader.read(body()).parts.filter { it.type == UmpPart.MEDIA }

        assertEquals("nine MEDIA parts in the captured response", 9, media.size)
        assertEquals(196_721, media.sumOf { it.payload.size })
        assertTrue("each begins with its own type marker", media.all { it.payload[0] == UmpPart.MEDIA.toByte() })
    }

    /**
     * Cut anywhere and the reader must report exactly what it could not use, so a caller
     * streaming the response carries the remainder into the next read. This is the case that
     * silently corrupts media if it is got wrong, and it happens on every response boundary.
     */
    @Test
    fun `truncating anywhere leaves a consistent remainder`() {
        val raw = body()
        val whole = UmpReader.read(raw)

        listOf(1, 100, 14_000, 20_000, 60_000, raw.size - 1).forEach { cut ->
            val partial = UmpReader.read(raw.copyOfRange(0, cut))
            assertTrue("consumed must never exceed what it was given at cut=$cut", partial.consumed <= cut)
            assertTrue(
                "a prefix can only yield a prefix of the parts at cut=$cut",
                partial.parts.size <= whole.parts.size,
            )
            assertEquals(
                "the parts it did read must match the full read at cut=$cut",
                whole.parts.take(partial.parts.size),
                partial.parts,
            )
        }
    }
}
