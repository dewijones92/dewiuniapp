package com.dewijones92.totum.sabr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Building the request body that YouTube actually accepted.
 *
 * The expected bytes here are not invented: the same encoding, sent live on 2026-07-31,
 * returned 212KB of media. An empty body instead returns
 * `RELOAD_PLAYER_RESPONSE: sabr.malformed_config`.
 */
class VideoPlaybackAbrRequestTest {

    @Test
    fun `protobuf varints are seven bits per byte, unlike UMP's`() {
        assertArrayEquals(byteArrayOf(0x00), Protobuf.varint(0))
        assertArrayEquals(byteArrayOf(0x7F), Protobuf.varint(127))
        // 128 needs a continuation byte — where a UMP varint would still be one byte at 128.
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), Protobuf.varint(128))
        assertArrayEquals(byteArrayOf(0xAC.toByte(), 0x02), Protobuf.varint(300))
    }

    @Test
    fun `the config goes in field 5 as a length-delimited value`() {
        val config = byteArrayOf(1, 2, 3)

        val body = VideoPlaybackAbrRequest(config).encode()

        // tag = (5 << 3) | 2 = 0x2A, then length 3, then the bytes.
        assertArrayEquals(byteArrayOf(0x2A, 0x03, 1, 2, 3), body)
    }

    @Test
    fun `a real-sized config encodes with a multi-byte length`() {
        val config = ByteArray(9_613) { it.toByte() }

        val body = VideoPlaybackAbrRequest(config).encode()

        // 9613 needs two protobuf varint bytes, so: tag + 2 length bytes + payload.
        assertEquals(1 + 2 + config.size, body.size)
        assertEquals(0x2A.toByte(), body[0])
    }

    @Test
    fun `player time is sent as a plain number before the config`() {
        val body = VideoPlaybackAbrRequest(byteArrayOf(9), playerTimeMs = 5_000).encode()

        // field 4, wire type 0 -> tag 0x20, then varint 5000.
        assertEquals(0x20.toByte(), body[0])
        assertArrayEquals(Protobuf.varint(5_000), body.copyOfRange(1, 3))
    }
}
