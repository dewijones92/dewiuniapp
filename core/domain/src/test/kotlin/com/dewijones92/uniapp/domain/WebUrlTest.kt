package com.dewijones92.uniapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WebUrlTest {

    @Test
    fun `parses https url`() {
        assertEquals("https://example.com/feed.xml", WebUrl.parse("https://example.com/feed.xml")?.value)
    }

    @Test
    fun `parses http url`() {
        assertNotNull(WebUrl.parse("http://example.com"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("https://example.com", WebUrl.parse("  https://example.com  ")?.value)
    }

    @Test
    fun `rejects other schemes`() {
        assertNull(WebUrl.parse("ftp://example.com"))
        assertNull(WebUrl.parse("file:///etc/passwd"))
        assertNull(WebUrl.parse("javascript:alert(1)"))
    }

    @Test
    fun `rejects relative and malformed input`() {
        assertNull(WebUrl.parse("example.com"))
        assertNull(WebUrl.parse("/feed.xml"))
        assertNull(WebUrl.parse(""))
        assertNull(WebUrl.parse("https://"))
        assertNull(WebUrl.parse("ht tp://example.com"))
    }

    @Test
    fun `of throws on invalid input`() {
        assertThrows(IllegalArgumentException::class.java) { WebUrl.of("not a url") }
    }
}
