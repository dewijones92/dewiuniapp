package com.dewijones92.totum.playback

import com.dewijones92.totum.domain.MediaItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The number that answers "can I keep watching this?".
 *
 * Every value here ends up on screen next to a stalling video, so a wrong one is a confident
 * lie at exactly the moment somebody is deciding whether to give up on the episode.
 */
class BufferAheadTest {

    private fun state(positionMs: Long, bufferedPositionMs: Long, durationMs: Long? = 600_000) =
        PlaybackState(
            itemId = MediaItemId("item"),
            title = "Title",
            artist = null,
            artworkUrl = null,
            isPlaying = true,
            positionMs = positionMs,
            durationMs = durationMs,
            speed = 1.0f,
            bufferedPositionMs = bufferedPositionMs,
        )

    @Test
    fun `it reports the seconds held beyond the playhead`() {
        val ahead = BufferAhead.of(state(positionMs = 10_000, bufferedPositionMs = 42_000), previous = null)

        assertEquals(32L, ahead?.seconds)
    }

    /**
     * A buffer BEHIND the playhead is nonsense but reachable — a seek lands ahead of what is
     * loaded, and for a moment the arithmetic is negative. "-4 seconds buffered" would be worse
     * than useless, so it floors at zero.
     */
    @Test
    fun `a playhead beyond the buffer reads as nothing, never a negative`() {
        val ahead = BufferAhead.of(state(positionMs = 50_000, bufferedPositionMs = 42_000), previous = null)

        assertEquals(0L, ahead?.seconds)
    }

    /**
     * Live has no end to be ahead of. Null rather than zero, because the caller then renders
     * nothing at all — a confident zero is worse than silence.
     */
    @Test
    fun `no duration means no gauge`() {
        assertNull(BufferAhead.of(state(0, 10_000, durationMs = null), previous = null))
    }

    /** The first reading has nothing to compare against and must not claim a direction. */
    @Test
    fun `the first sample is never falling`() {
        assertFalse(BufferAhead.of(state(0, 5_000), previous = null)!!.falling)
    }

    @Test
    fun `a shrinking buffer is falling`() {
        val first = BufferAhead.of(state(positionMs = 0, bufferedPositionMs = 30_000), previous = null)!!
        val second = BufferAhead.of(state(positionMs = 10_000, bufferedPositionMs = 25_000), previous = first)!!

        assertEquals(15L, second.seconds)
        assertTrue(second.falling)
    }

    /**
     * The healthy case, and the one most easily got wrong: a buffer holding STEADY while playing
     * is being refilled at exactly the rate it drains. Reporting that as falling would put a
     * warning on every well-behaved stream.
     */
    @Test
    fun `a steady buffer is not falling`() {
        val first = BufferAhead.of(state(positionMs = 0, bufferedPositionMs = 30_000), previous = null)!!
        val second = BufferAhead.of(state(positionMs = 10_000, bufferedPositionMs = 40_000), previous = first)!!

        assertEquals(30L, second.seconds)
        assertFalse(second.falling)
    }

    @Test
    fun `a growing buffer is not falling`() {
        val first = BufferAhead.of(state(positionMs = 0, bufferedPositionMs = 10_000), previous = null)!!
        val second = BufferAhead.of(state(positionMs = 0, bufferedPositionMs = 40_000), previous = first)!!

        assertFalse(second.falling)
    }

    /** "Low" is what decides whether the number is shown at all, so its edge is pinned. */
    @Test
    fun `low is at most ten seconds`() {
        assertTrue(BufferAhead(seconds = BufferAhead.LOW_SECONDS, falling = false).low)
        assertFalse(BufferAhead(seconds = BufferAhead.LOW_SECONDS + 1, falling = false).low)
    }
}
