package com.dewijones92.totum.playback

import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A player frozen at the end of an item, which is neither an end nor an error and so is
 * invisible to [AutoAdvancer] and [ExpiredStreamRecovery] alike.
 *
 * The numbers are the real ones from the 0.1.230 report: a 2 512 000ms video stuck at
 * 2 506 062ms — seven seconds short — buffering for 46 seconds with 65 items queued behind
 * it, until Dewi picked the next one by hand.
 *
 * These tests hold the state **completely still** while time passes, because that is what a
 * stall actually is. The first version of the watchdog collected the state flow instead of
 * sampling it, and these tests failed: a `StateFlow` drops a value equal to the previous one,
 * so a frozen player emits once and then nothing, and an emission-driven timer never gets a
 * second look. That would have shipped a watchdog that silently never fired.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StallWatchdogTest {

    private val states = MutableStateFlow<PlaybackState?>(null)
    private var advanced = 0
    private var enabled = true

    private fun TestScope.watchdog() = StallWatchdog(
        states = states,
        advance = {
            advanced++
            true
        },
        isEnabled = { enabled },
        scope = backgroundScope,
    ).also { it.start() }

    @Test
    fun `the real report — frozen seven seconds from the end — advances`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true)
        advanceTimeBy(46_000)

        assertEquals(1, advanced)
    }

    @Test
    fun `a buffer shorter than the stall window is left alone`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true)
        advanceTimeBy(19_000)

        assertEquals(0, advanced)
    }

    @Test
    fun `a long stall advances exactly once`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true)
        advanceTimeBy(600_000)

        assertEquals(1, advanced)
    }

    @Test
    fun `a stall in the middle of an item is left to the player`() = runTest {
        watchdog()
        states.value = state(positionMs = 500_000, buffering = true)
        advanceTimeBy(600_000)

        assertEquals(0, advanced)
    }

    /** A paused player has a frozen position too, and is not stuck. */
    @Test
    fun `a paused player is not a stall`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = false)
        advanceTimeBy(600_000)

        assertEquals(0, advanced)
    }

    @Test
    fun `buffering that keeps making progress is not a stall`() = runTest {
        watchdog()
        repeat(100) {
            states.value = state(REPORTED_POSITION + it * 4_000, buffering = true)
            advanceTimeBy(6_000)
        }

        assertEquals(0, advanced)
    }

    /** Recovering before the window closes must clear the clock, not bank the time. */
    @Test
    fun `a stall that recovers does not count towards a later one`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true)
        advanceTimeBy(15_000)
        states.value = state(REPORTED_POSITION + 1_000, buffering = false)
        advanceTimeBy(15_000)
        states.value = state(REPORTED_POSITION + 1_000, buffering = true)
        advanceTimeBy(15_000)

        assertEquals(0, advanced)
    }

    @Test
    fun `auto-play off means a stall is reported but nothing is played`() = runTest {
        enabled = false
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true)
        advanceTimeBy(46_000)

        assertEquals(0, advanced)
    }

    @Test
    fun `each item gets its own stall`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true, id = "a")
        advanceTimeBy(46_000)
        states.value = state(REPORTED_POSITION, buffering = true, id = "b")
        advanceTimeBy(46_000)

        assertEquals(2, advanced)
    }

    /** An item with no known duration cannot be judged to be at its end. */
    @Test
    fun `an unknown duration is never treated as the end`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true, durationMs = null)
        advanceTimeBy(600_000)

        assertEquals(0, advanced)
    }

    /** Nothing playing at all must not be mistaken for a frozen player. */
    @Test
    fun `no state is not a stall`() = runTest {
        watchdog()
        runCurrent()
        advanceTimeBy(600_000)

        assertEquals(0, advanced)
    }

    private fun state(
        positionMs: Long,
        buffering: Boolean,
        id: String = "vid",
        durationMs: Long? = REPORTED_DURATION,
    ) = PlaybackState(
        itemId = MediaItemId(id),
        title = id,
        artist = null,
        artworkUrl = null,
        kind = MediaKind.VIDEO,
        isPlaying = false,
        positionMs = positionMs,
        durationMs = durationMs,
        speed = 1f,
        isBuffering = buffering,
    )

    private companion object {
        const val REPORTED_POSITION = 2_506_062L
        const val REPORTED_DURATION = 2_512_000L
    }

    /**
     * The same defect that broke [AutoAdvancer] on 2026-08-01, checked here before it was ever
     * reported: an item rescued once could never be rescued again, so replaying it and stalling
     * again left the queue stopped with nothing in the log to explain it.
     */
    @Test
    fun `an item that stalls, recovers, then stalls again is rescued twice`() = runTest {
        watchdog()
        states.value = state(REPORTED_POSITION, buffering = true)
        advanceTimeBy(46_000)
        assertEquals(1, advanced)

        // Progress on the SAME item, which is what makes the first rescue spent.
        states.value = state(REPORTED_POSITION + 5_000, buffering = true)
        advanceTimeBy(6_000)

        // ...and then it freezes again at the new position.
        advanceTimeBy(46_000)

        assertEquals("a second stall on the same item must be rescued too", 2, advanced)
    }
}
