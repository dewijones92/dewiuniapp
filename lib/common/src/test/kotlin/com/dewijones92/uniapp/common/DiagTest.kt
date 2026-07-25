package com.dewijones92.uniapp.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagTest {

    private val written = mutableListOf<Triple<Diag.Level, String, String>>()

    @Before
    fun setUp() {
        Breadcrumbs.clear()
        Diag.sink = Diag.Sink { level, tag, message, _ -> written += Triple(level, tag, message) }
    }

    @After
    fun tearDown() {
        Breadcrumbs.clear()
        Diag.sink = Diag.Sink { _, _, _, _ -> }
    }

    @Test
    fun `logging records a breadcrumb and prints it`() {
        Diag.log("queue", "size=3")

        assertEquals(listOf("queue" to "size=3"), Breadcrumbs.snapshot().map { it.tag to it.message })
        assertEquals(Triple(Diag.Level.INFO, "queue", "size=3"), written.single())
    }

    @Test
    fun `warn includes the throwable in the remembered text`() {
        Diag.warn("download", "failed", IllegalStateException("no space"))

        val remembered = Breadcrumbs.snapshot().single().message
        assertTrue(remembered, remembered.contains("failed"))
        assertTrue(remembered, remembered.contains("IllegalStateException"))
        assertTrue(remembered, remembered.contains("no space"))
        assertEquals(Diag.Level.WARN, written.single().first)
    }

    @Test
    fun `warn without a throwable keeps the message clean`() {
        Diag.warn("download", "failed: 404")

        assertEquals("failed: 404", Breadcrumbs.snapshot().single().message)
    }

    /** The whole point of the buffer: a long session must not grow without limit. */
    @Test
    fun `oldest breadcrumbs fall off once the buffer is full`() {
        repeat(500) { Diag.log("tick", "event $it") }

        val snapshot = Breadcrumbs.snapshot()
        assertEquals(400, snapshot.size)
        assertEquals("event 100", snapshot.first().message)
        assertEquals("event 499", snapshot.last().message)
    }

    @Test
    fun `snapshot is oldest first so a report reads as a story`() {
        Diag.log("a", "first")
        Diag.log("b", "second")

        assertEquals(listOf("first", "second"), Breadcrumbs.snapshot().map { it.message })
    }

    @Test
    fun `entries are stamped with a readable wall-clock time`() {
        Diag.log("a", "x")

        val formatted = Breadcrumbs.formatTime(Breadcrumbs.snapshot().single().atEpochMs)
        assertTrue(formatted, formatted.matches(Regex("""\d{2}:\d{2}:\d{2}\.\d{3}""")))
    }

    /** A module with no sink installed (unit tests, pure-JVM callers) must still record. */
    @Test
    fun `breadcrumbs are recorded even with the silent default sink`() {
        Diag.sink = Diag.Sink { _, _, _, _ -> }

        Diag.log("engine", "extracting")

        assertEquals("extracting", Breadcrumbs.snapshot().single().message)
    }
}
