package com.dewijones92.totum.ui.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A brightness set by gesture survives the video ending.
 *
 * The window override must be dropped when video goes away, or the queue and settings screens
 * inherit a dimmed window. That dropped the CHOICE too, so on a queue of videos the brightness
 * reset at every track change — the app changing a setting nobody touched, which is exactly what
 * Dewi asked to stop on 2026-08-05.
 */
class ChosenBrightnessTest {

    @Before
    @After
    fun clean() = ChosenBrightness.forget()

    @Test
    fun `nothing chosen means follow the system`() {
        assertEquals(ChosenBrightness.FOLLOW_SYSTEM, ChosenBrightness.value, 0f)
        assertFalse("with nothing to restore, a video must not override the window", ChosenBrightness.isSet)
    }

    @Test
    fun `a gesture is remembered`() {
        ChosenBrightness.choose(0.3f)

        assertEquals(0.3f, ChosenBrightness.value, 0f)
        assertTrue(ChosenBrightness.isSet)
    }

    /** Full dark is a real choice, not "unset" — the boundary the negative sentinel sits next to. */
    @Test
    fun `zero is a choice, not the absence of one`() {
        ChosenBrightness.choose(0f)

        assertEquals(0f, ChosenBrightness.value, 0f)
        assertTrue("0 must count as set, or full dark would be silently ignored", ChosenBrightness.isSet)
    }

    @Test
    fun `a later gesture replaces the earlier one`() {
        ChosenBrightness.choose(0.3f)
        ChosenBrightness.choose(0.8f)

        assertEquals(0.8f, ChosenBrightness.value, 0f)
    }

    /** A drag past either end is clamped rather than stored as nonsense. */
    @Test
    fun `values outside the range are clamped`() {
        ChosenBrightness.choose(2f)
        assertEquals(1f, ChosenBrightness.value, 0f)

        ChosenBrightness.choose(-5f)
        assertEquals("and never back to the follow-the-system sentinel", 0f, ChosenBrightness.value, 0f)
    }
}
