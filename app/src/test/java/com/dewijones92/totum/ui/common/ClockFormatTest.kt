package com.dewijones92.totum.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The duration chip. This used to be "N min" inside the subtitle, which rounded a
 * 45-second Short to "0 min" and was the first thing an ellipsis ate.
 */
class ClockFormatTest {

    @Test
    fun `under an hour is m colon ss`() {
        assertEquals("0:45", formatClock(45.seconds))
        assertEquals("12:34", formatClock(12.minutes + 34.seconds))
        assertEquals("59:59", formatClock(59.minutes + 59.seconds))
    }

    @Test
    fun `an hour or more grows a field, with padded minutes`() {
        assertEquals("1:00:00", formatClock(1.hours))
        assertEquals("1:02:45", formatClock(1.hours + 2.minutes + 45.seconds))
        assertEquals("10:00:00", formatClock(10.hours))
    }

    @Test
    fun `sub-second durations still read as a length, not as nothing`() {
        assertEquals("0:00", formatClock(500.milliseconds()))
    }

    private fun Int.milliseconds() = (this / MILLIS_PER_SECOND.toDouble()).seconds

    private companion object {
        const val MILLIS_PER_SECOND = 1000
    }
}
