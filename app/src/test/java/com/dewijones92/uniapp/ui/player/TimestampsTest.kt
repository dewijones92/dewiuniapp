package com.dewijones92.uniapp.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampsTest {

    @Test
    fun `parses m ss to milliseconds`() {
        val matches = Timestamps.find("Intro 0:00 then 1:30 later")
        assertEquals(listOf(0L, 90_000L), matches.map { it.positionMs })
    }

    @Test
    fun `parses h mm ss`() {
        val matches = Timestamps.find("Deep dive 1:02:03")
        assertEquals(listOf(3_723_000L), matches.map { it.positionMs })
    }

    @Test
    fun `ranges point at the matched text`() {
        val text = "Chapter at 2:05 here"
        val match = Timestamps.find(text).single()
        assertEquals("2:05", text.substring(match.range))
    }

    @Test
    fun `ignores non-timestamps and out-of-range values`() {
        // A lone number, a ratio-like 99:99, and a year aren't chapter marks.
        assertTrue(Timestamps.find("scores 12 to 3, ratio 99:99, in 2026").isEmpty())
    }

    @Test
    fun `does not match inside a longer digit run`() {
        assertTrue(Timestamps.find("id 123:456 or 1234").isEmpty())
    }
}
