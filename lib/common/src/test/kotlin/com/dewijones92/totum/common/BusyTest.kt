package com.dewijones92.totum.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The seam behind the global loading bar.
 *
 * The behaviour that matters is not "does it report busy" but **does it ever get stuck**: a
 * handle that fails to release leaves the indicator lit forever, which is worse than having
 * no indicator, since the user learns to ignore it.
 */
class BusyTest {

    @Before fun setUp() = Busy.reset()

    @Test
    fun `work is reported while it runs and gone afterwards`() = runTest {
        assertTrue(Busy.work.value.isEmpty())

        Busy.during("loading channel") {
            assertEquals(listOf("loading channel"), Busy.work.value)
        }

        assertTrue("must be idle again", Busy.work.value.isEmpty())
    }

    /**
     * The case that would otherwise light the bar permanently. A failed HTTP request is the
     * common one, and it is exactly when the user most needs the app to look responsive.
     */
    @Test
    fun `a failure still releases the work`() = runTest {
        runCatching { Busy.during<Unit>("doomed") { error("network died") } }

        assertTrue("a throw must not leave the app busy", Busy.work.value.isEmpty())
    }

    /**
     * Two concurrent requests to the same endpoint share a label. Removing every match when
     * the first finished would report idle while the second was still running — so exactly
     * one is removed.
     */
    @Test
    fun `two pieces of identical work are counted separately`() {
        val first = Busy.begin("api.example/thing")
        val second = Busy.begin("api.example/thing")
        assertEquals(2, Busy.work.value.size)

        first.close()
        assertEquals("still busy with the second", 1, Busy.work.value.size)

        second.close()
        assertTrue(Busy.work.value.isEmpty())
    }

    @Test
    fun `closing twice does not un-busy other work`() {
        val handle = Busy.begin("thing")
        val other = Busy.begin("thing")

        handle.close()
        handle.close()

        assertEquals(1, Busy.work.value.size)
        other.close()
    }

    /** Names, not a count, so a report can say WHAT the app was waiting on. */
    @Test
    fun `work is named`() {
        Busy.begin("www.youtube.com/youtubei/v1/browse")
        Busy.begin("extracting dQw4w9WgXcQ")

        assertEquals(
            listOf("www.youtube.com/youtubei/v1/browse", "extracting dQw4w9WgXcQ"),
            Busy.work.value,
        )
    }
}
