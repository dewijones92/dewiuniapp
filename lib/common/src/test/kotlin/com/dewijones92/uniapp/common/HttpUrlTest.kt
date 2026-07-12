package com.dewijones92.uniapp.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpUrlTest {

    @Test
    fun `parses https url`() {
        assertEquals("https://example.com/feed.xml", HttpUrl.parse("https://example.com/feed.xml")?.value)
    }

    @Test
    fun `parses http url`() {
        assertNotNull(HttpUrl.parse("http://example.com"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("https://example.com", HttpUrl.parse("  https://example.com  ")?.value)
    }

    @Test
    fun `rejects other schemes`() {
        assertNull(HttpUrl.parse("ftp://example.com"))
        assertNull(HttpUrl.parse("file:///etc/passwd"))
        assertNull(HttpUrl.parse("javascript:alert(1)"))
        assertNull(HttpUrl.parse("rtmp://example.com/stream"))
    }

    @Test
    fun `rejects relative and malformed input`() {
        assertNull(HttpUrl.parse("example.com"))
        assertNull(HttpUrl.parse("/feed.xml"))
        assertNull(HttpUrl.parse(""))
        assertNull(HttpUrl.parse("https://"))
        assertNull(HttpUrl.parse("ht tp://example.com"))
    }

    @Test
    fun `of throws on invalid input`() {
        assertThrows(IllegalArgumentException::class.java) { HttpUrl.of("not a url") }
    }
}
