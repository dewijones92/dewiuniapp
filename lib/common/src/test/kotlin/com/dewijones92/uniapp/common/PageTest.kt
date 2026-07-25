package com.dewijones92.uniapp.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTest {

    private val first = PageToken("tok-1")
    private val second = PageToken("tok-2")

    @Test
    fun `a page with a token has more`() {
        assertTrue(Page(listOf("a"), first).hasMore)
    }

    /** "No more pages" is an ordinary page, which is what lets RSS share the seam. */
    @Test
    fun `the last page has no token and no more`() {
        val page = Page.last(listOf("a", "b"))

        assertFalse(page.hasMore)
        assertNull(page.next)
    }

    @Test
    fun `an empty page is a last page`() {
        assertEquals(Page.last(emptyList<String>()), Page.empty<String>())
    }

    @Test
    fun `map keeps the continuation, so a mapped page is still pageable`() {
        val mapped = Page(listOf(1, 2), first).map { it.toString() }

        assertEquals(listOf("1", "2"), mapped.items)
        assertEquals(first, mapped.next)
    }

    @Test
    fun `append concatenates and advances the token`() {
        val combined = Page(listOf("a"), first).append(Page(listOf("b"), second)) { it }

        assertEquals(listOf("a", "b"), combined.items)
        assertEquals(second, combined.next)
    }

    /**
     * YouTube does return overlapping pages. A duplicate key in a LazyColumn is a crash,
     * so deduplication belongs in the seam rather than in each caller.
     */
    @Test
    fun `append drops items already present, keeping the first seen`() {
        val combined = Page(listOf("a" to 1), first).append(Page(listOf("a" to 2, "b" to 3))) { it.first }

        assertEquals(listOf("a" to 1, "b" to 3), combined.items)
    }

    @Test
    fun `appending a last page ends the sequence`() {
        val combined = Page(listOf("a"), first).append(Page.last(listOf("b"))) { it }

        assertFalse(combined.hasMore)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a blank token is not representable`() {
        PageToken("  ")
    }

    /** Tokens are long and opaque; printing one is pure noise in a log. */
    @Test
    fun `a token redacts itself`() {
        assertFalse(PageToken("secret-continuation").toString().contains("secret"))
    }
}
