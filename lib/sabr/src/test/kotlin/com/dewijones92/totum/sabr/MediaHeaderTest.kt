package com.dewijones92.totum.sabr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a real `MEDIA_HEADER`.
 *
 * The bytes below are verbatim from a live SABR response captured 2026-07-31 — a whole
 * 52-byte header, no media payload in it, so nothing here is anyone's copyrighted video. It is
 * the second header of the response, which declared itag 396 and was followed by bytes
 * beginning `ftypdash`; that is what confirms field 3 really is the itag rather than a
 * plausible-looking number.
 */
class MediaHeaderTest {

    /** Header #2 of the captured response: init segment for itag 396. */
    private val realHeader = (
        "0801120b394b774431696461677645188c0320e893c3f6d0f99503300040015080bd026a0c088c03" +
            "10e893c3f6d0f99503709c19"
        ).hexToBytes()

    private fun String.hexToBytes() =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `reads a real init-segment header`() {
        val header = MediaHeader.parse(realHeader)!!

        assertEquals(1L, header.headerId)
        assertEquals("9KwD1idagvE", header.videoId)
        assertEquals("itag 396, whose bytes began ftypdash", 396, header.itag)
        assertEquals(1_785_388_088_740_328L, header.lastModified)
        assertTrue("field 8 = 1 marks the container header, not playable media", header.isInitSegment)
        assertEquals("first run of this format starts at zero", 0L, header.startBytes)
        assertEquals(3_228L, header.contentLength)
    }

    /**
     * The continuation of that same format later in the response. Its offset is what tells a
     * player where the bytes belong; getting it wrong splices media at the wrong place, which
     * decodes as corruption rather than failing outright.
     */
    @Test
    fun `a continuation header carries a non-zero offset and is not an init segment`() {
        // Header #4 of the same response: itag 396 again, offset 3228, length 46058.
        val continuation = Protobuf.number(1, 3) +
            Protobuf.bytes(2, "9KwD1idagvE".toByteArray()) +
            Protobuf.number(3, 396) +
            Protobuf.number(4, 1_785_388_088_740_328L) +
            Protobuf.number(6, 3_228) +
            Protobuf.number(8, 0) +
            Protobuf.number(14, 46_058)

        val header = MediaHeader.parse(continuation)!!

        assertEquals(3L, header.headerId)
        assertEquals(396, header.itag)
        assertEquals(3_228L, header.startBytes)
        assertFalse(header.isInitSegment)
        assertEquals(46_058L, header.contentLength)
    }

    /**
     * YouTube adds fields we have never seen. One of them must not cost us the whole header,
     * so an unknown field is skipped rather than aborting the read.
     */
    @Test
    fun `unknown fields are ignored rather than fatal`() {
        val withExtras = Protobuf.number(1, 7) +
            Protobuf.number(3, 251) +
            Protobuf.bytes(99, ByteArray(20)) +
            Protobuf.number(14, 500)

        val header = MediaHeader.parse(withExtras)!!

        assertEquals(7L, header.headerId)
        assertEquals(251, header.itag)
        assertEquals(500L, header.contentLength)
    }

    @Test
    fun `an empty or unreadable payload is null, not a header of zeroes`() {
        assertNull(MediaHeader.parse(ByteArray(0)))
    }
}
