package com.dewijones92.totum.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gesture arithmetic, with no Activity or AudioManager behind it — brightness is
 * still tracked and reported, which is the part that can be wrong. Volume needs a real
 * AudioManager, so it is exercised on a device instead.
 */
class VideoGestureStateTest {

    private val state = VideoGestureState(activity = null, audio = null)

    private fun drag(pixels: Float) = state.adjust(VideoAdjustment.BRIGHTNESS, pixels, STAGE_HEIGHT)

    @Test
    fun `nothing is shown until a drag begins`() {
        assertNull(state.feedback)
    }

    @Test
    fun `beginning a drag shows the current level`() {
        state.begin(VideoAdjustment.BRIGHTNESS)

        assertEquals(VideoAdjustment.BRIGHTNESS, state.feedback?.kind)
        assertNotNull(state.feedback)
    }

    /** Up is more, as in every player. A drag reports negative pixels for upward. */
    @Test
    fun `dragging up raises brightness and down lowers it`() {
        state.begin(VideoAdjustment.BRIGHTNESS)
        val start = state.feedback!!.fraction

        drag(-(STAGE_HEIGHT / 4))
        val raised = state.feedback!!.fraction
        assertEquals(start + 0.25f, raised, TOLERANCE)

        drag(STAGE_HEIGHT / 2)
        assertEquals(raised - 0.5f, state.feedback!!.fraction, TOLERANCE)
    }

    /** A full swipe of the stage is the full range, so it feels the same at any size. */
    @Test
    fun `a full-height swipe covers the whole range`() {
        state.begin(VideoAdjustment.BRIGHTNESS)
        drag(-STAGE_HEIGHT)
        assertEquals(1f, state.feedback!!.fraction, TOLERANCE)

        drag(STAGE_HEIGHT)
        assertEquals(0f, state.feedback!!.fraction, TOLERANCE)
    }

    @Test
    fun `it clamps rather than running past either end`() {
        state.begin(VideoAdjustment.BRIGHTNESS)
        repeat(5) { drag(-STAGE_HEIGHT) }
        assertEquals(1f, state.feedback!!.fraction, TOLERANCE)

        repeat(5) { drag(STAGE_HEIGHT) }
        assertEquals(0f, state.feedback!!.fraction, TOLERANCE)
    }

    /** A zero-height stage would divide by zero; it must simply do nothing. */
    @Test
    fun `an unmeasured stage is ignored`() {
        state.begin(VideoAdjustment.BRIGHTNESS)
        val before = state.feedback!!.fraction

        state.adjust(VideoAdjustment.BRIGHTNESS, -100f, stageHeight = 0f)

        assertEquals(before, state.feedback!!.fraction, TOLERANCE)
    }

    @Test
    fun `the readout disappears when the drag ends`() {
        state.begin(VideoAdjustment.BRIGHTNESS)
        state.end()
        assertNull(state.feedback)
    }

    private companion object {
        const val STAGE_HEIGHT = 1000f
        const val TOLERANCE = 0.001f
    }
}
