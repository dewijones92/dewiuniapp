package com.dewijones92.uniapp.data

import com.dewijones92.uniapp.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ChapterJsonTest {

    @Test
    fun `round-trips chapters, preserving order and titles with special characters`() {
        val chapters = listOf(
            Chapter(0.seconds, "Cold \"open\""),
            Chapter(90.seconds, "Main & topic"),
            Chapter(125.milliseconds, "Odd offset"),
        )

        val decoded = ChapterJson.decode(ChapterJson.encode(chapters))

        assertEquals(chapters, decoded)
    }

    @Test
    fun `empty list encodes to null and null decodes to empty`() {
        assertNull(ChapterJson.encode(emptyList()))
        assertTrue(ChapterJson.decode(null).isEmpty())
    }

    @Test
    fun `malformed json decodes to empty, not a crash`() {
        assertTrue(ChapterJson.decode("not json").isEmpty())
        assertTrue(ChapterJson.decode("{\"not\":\"an array\"}").isEmpty())
    }

    @Test
    fun `a single bad element is skipped, not fatal to the rest`() {
        val json = """[{"s":0,"t":"Good"},{"s":"oops","t":"bad start"},{"t":"no start"},{"s":2000,"t":"Also good"}]"""

        val decoded = ChapterJson.decode(json)

        assertEquals(listOf(Chapter(0.seconds, "Good"), Chapter(2000.milliseconds, "Also good")), decoded)
    }

    @Test
    fun `negative or blank-title elements are dropped`() {
        val json = """[{"s":-5,"t":"negative"},{"s":10,"t":"  "},{"s":10,"t":"Kept"}]"""

        assertEquals(listOf(Chapter(10.milliseconds, "Kept")), ChapterJson.decode(json))
    }
}
