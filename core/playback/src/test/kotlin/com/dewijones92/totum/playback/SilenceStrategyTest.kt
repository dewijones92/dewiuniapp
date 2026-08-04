package com.dewijones92.totum.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which silence mechanism applies, and why it is not one.
 *
 * Getting this wrong is not cosmetic in either direction: removing samples under a video desyncs
 * the picture by seconds, and speeding up under a podcast is the audible stutter that made
 * skip-silence feel worse here than in AntennaPod.
 */
class SilenceStrategyTest {

    /** Audio only — AntennaPod's mechanism, and seamless because nothing tracks the audio clock. */
    @Test
    fun `audio-only removes the silent samples`() {
        assertEquals(
            SilenceStrategy.REMOVE_SAMPLES,
            SilenceStrategy.of(enabled = true, hasVideo = false),
        )
    }

    /**
     * With a picture, samples must not be removed: the audio gets shorter and the video clock does
     * not, so the two drift apart — measured at ~6s over a 20s clip.
     */
    @Test
    fun `video speeds through the gap instead, so nothing desyncs`() {
        assertEquals(
            SilenceStrategy.SPEED_UP,
            SilenceStrategy.of(enabled = true, hasVideo = true),
        )
    }

    @Test
    fun `switched off, neither mechanism runs`() {
        assertEquals(SilenceStrategy.OFF, SilenceStrategy.of(enabled = false, hasVideo = false))
        assertEquals(SilenceStrategy.OFF, SilenceStrategy.of(enabled = false, hasVideo = true))
    }
}
