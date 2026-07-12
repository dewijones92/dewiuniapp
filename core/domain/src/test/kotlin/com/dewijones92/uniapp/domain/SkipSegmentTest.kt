package com.dewijones92.uniapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SkipSegmentTest {

    private val segments = listOf(
        SkipSegment(start = 10.seconds, end = 20.seconds),
        SkipSegment(start = 60.seconds, end = 90.seconds),
    )

    @Test
    fun `outside any segment there is nothing to skip`() {
        assertNull(segments.skipTargetFor(5.seconds))
        assertNull(segments.skipTargetFor(20.seconds))
        assertNull(segments.skipTargetFor(100.seconds))
    }

    @Test
    fun `inside a segment the target is its end`() {
        assertEquals(20.seconds, segments.skipTargetFor(10.seconds))
        assertEquals(20.seconds, segments.skipTargetFor(15.seconds))
        assertEquals(90.seconds, segments.skipTargetFor(89.seconds))
    }

    @Test
    fun `chained segments are skipped in one hop`() {
        val chained = listOf(
            SkipSegment(start = 10.seconds, end = 20.seconds),
            SkipSegment(start = 20.seconds, end = 30.seconds),
        )
        assertEquals(30.seconds, chained.skipTargetFor(12.seconds))
    }

    @Test
    fun `invalid segments are unrepresentable`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkipSegment(start = 5.seconds, end = 5.seconds)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkipSegment(start = (-1).seconds, end = 5.seconds)
        }
    }
}
