package com.dewijones92.totum.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VitalsTest {

    @Before
    fun reset() = Vitals.clear()

    @After
    fun tidy() = Vitals.clear()

    @Test
    fun `counters accumulate across a session`() {
        Vitals.add("playback.stalls")
        Vitals.add("playback.stalls")
        Vitals.add("playback.bufferingMs", 1500)

        assertEquals("2", Vitals.snapshot()["playback.stalls"])
        assertEquals("1500", Vitals.snapshot()["playback.bufferingMs"])
    }

    @Test
    fun `a value keeps only the latest`() {
        Vitals.set("playback.lastError", "first")
        Vitals.set("playback.lastError", "second")

        assertEquals("second", Vitals.snapshot()["playback.lastError"])
    }

    /** Sorted so two reports from different runs can be diffed line by line. */
    @Test
    fun `the snapshot is ordered by name`() {
        Vitals.set("zebra", "1")
        Vitals.add("alpha")
        Vitals.set("middle", "x")

        assertEquals(listOf("alpha", "middle", "zebra"), Vitals.snapshot().keys.toList())
    }

    @Test
    fun `an untouched name is simply absent`() {
        assertEquals(emptyMap<String, String>(), Vitals.snapshot())
    }
}
