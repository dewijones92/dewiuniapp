package com.dewijones92.uniapp.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaUrlTest {

    @Test
    fun `parses absolute http and https urls`() {
        assertEquals("https://youtu.be/abc123", MediaUrl.parse("https://youtu.be/abc123")?.value)
        assertEquals("http://example.com/watch", MediaUrl.parse(" http://example.com/watch ")?.value)
    }

    @Test
    fun `rejects non-http schemes and malformed input`() {
        assertNull(MediaUrl.parse("rtmp://example.com/stream"))
        assertNull(MediaUrl.parse("watch?v=abc"))
        assertNull(MediaUrl.parse(""))
        assertNull(MediaUrl.parse("https://"))
    }

    @Test
    fun `of throws on invalid input`() {
        assertThrows(IllegalArgumentException::class.java) { MediaUrl.of("nope") }
    }
}
