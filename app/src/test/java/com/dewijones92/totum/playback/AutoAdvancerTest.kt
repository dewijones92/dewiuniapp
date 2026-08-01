package com.dewijones92.totum.playback

import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * End-of-item advance. This moved off the UI's lifecycle because a composable effect fed by
 * `collectAsStateWithLifecycle` stops being fed when the activity stops — so a phone in a
 * pocket never advanced. These tests drive the state flow directly, which is the whole point:
 * no composition involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoAdvancerTest {

    private val states = MutableStateFlow<PlaybackState?>(null)
    private var advanced = 0
    private var fellBackToRelated = 0
    private var enabled = true
    private var queueHasNext = true

    private fun TestScope.advancer() = AutoAdvancer(
        states = states,
        advance = {
            advanced++
            queueHasNext
        },
        whenQueueEmpty = { fellBackToRelated++ },
        isEnabled = { enabled },
        scope = backgroundScope,
    ).also { it.start() }

    @Test
    fun `an ended item advances the queue`() = runTest {
        advancer()
        runCurrent()
        playThenEnd("a")

        assertEquals(1, advanced)
    }

    @Test
    fun `an item still playing does not advance`() = runTest {
        advancer()
        runCurrent()
        states.value = state("a", ended = false)
        runCurrent()

        assertEquals(0, advanced)
    }

    /**
     * The player re-emits state on every position tick, so without deduping the transition
     * this would fire the advance dozens of times per item.
     */
    @Test
    fun `repeated state while ended advances only once`() = runTest {
        advancer()
        runCurrent()
        states.value = state("a", ended = false)
        runCurrent()
        repeat(5) {
            states.value = state("a", ended = true, positionMs = it.toLong())
            runCurrent()
        }

        assertEquals(1, advanced)
    }

    @Test
    fun `auto-play off means no advance`() = runTest {
        enabled = false
        advancer()
        runCurrent()
        playThenEnd("a")

        assertEquals(0, advanced)
    }

    /**
     * Shorts are advanced like everything else. The reel used to page itself from a composable
     * and set a suppression flag to keep this out of the way — which meant a short ending in a
     * pocket went nowhere, because both mechanisms were asleep. There is no longer any item
     * this refuses to advance past.
     */
    @Test
    fun `a short is advanced past like any other item`() = runTest {
        advancer()
        runCurrent()
        playThenEnd("a-short")

        assertEquals(1, advanced)
    }

    @Test
    fun `an empty queue falls back to a related video`() = runTest {
        queueHasNext = false
        advancer()
        runCurrent()
        playThenEnd("a")

        assertEquals(1, advanced)
        assertEquals(1, fellBackToRelated)
    }

    @Test
    fun `each item gets its own end`() = runTest {
        advancer()
        runCurrent()
        playThenEnd("a")
        playThenEnd("b")

        assertEquals(2, advanced)
    }

    /**
     * Re-enabling the setting must not retroactively advance an item that already ended while
     * it was off — the state is unchanged, so nothing should fire until the next end.
     */
    @Test
    fun `enabling auto-play does not advance an already-ended item`() = runTest {
        enabled = false
        advancer()
        runCurrent()
        playThenEnd("a")

        enabled = true
        runCurrent()

        assertEquals(0, advanced)
    }

    /**
     * Connecting to the playback session reports whatever it currently holds. After a process
     * restart that can be an item which ended long ago, and acting on it would skip an item
     * the instant the app launched. The reel screen guards the same thing.
     */
    @Test
    fun `an item already ended when we start is not advanced past`() = runTest {
        states.value = state("a", ended = true)
        advancer()
        runCurrent()

        assertEquals(0, advanced)
    }

    @Test
    fun `but a genuine end after starting still advances`() = runTest {
        states.value = state("a", ended = true)
        advancer()
        runCurrent()
        states.value = state("b", ended = false)
        runCurrent()
        states.value = state("b", ended = true)
        runCurrent()

        assertEquals(1, advanced)
    }

    /** A first state that is mid-playback must not consume the item's real end. */
    @Test
    fun `a first state that is still playing does not swallow its end`() = runTest {
        states.value = state("a", ended = false)
        advancer()
        runCurrent()
        states.value = state("a", ended = true)
        runCurrent()

        assertEquals(1, advanced)
    }

    /**
     * Plays [id] and then ends it — the real sequence. Tests that jumped straight to an ended
     * state were exercising the already-ended-on-connect path by accident, which is now
     * deliberately ignored, so they have to start from playing like the player does.
     */
    private fun TestScope.playThenEnd(id: String) {
        states.value = state(id, ended = false)
        runCurrent()
        states.value = state(id, ended = true)
        runCurrent()
    }

    private fun state(id: String, ended: Boolean, positionMs: Long = 0) = PlaybackState(
        itemId = MediaItemId(id),
        title = id,
        artist = null,
        artworkUrl = null,
        kind = MediaKind.VIDEO,
        isPlaying = false,
        positionMs = positionMs,
        durationMs = 1_000,
        speed = 1f,
        hasEnded = ended,
    )

    /**
     * Replaying an item the queue has already advanced past must advance again.
     *
     * The exact sequence from report 0.1.258, which is what Dewi hit: `40pRi5wMBwA` ended at
     * 05:04:44 and advanced correctly; he played it again at 08:22; it ended at 08:22:58 and the
     * advancer refused with "already handled this item's end" — about an end from three hours
     * and one other item earlier. Autoplay just stopped.
     */
    @Test
    fun `an item played again advances again when it ends`() = runTest {
        advancer()
        runCurrent()
        playThenEnd("a")
        // The queue moves on, exactly as it did in the report.
        states.value = state("b", ended = false)
        runCurrent()

        playThenEnd("a")

        assertEquals("the second end of the same item must advance too", 2, advanced)
    }

    /** The same replay without an item in between — going straight back to what just ended. */
    @Test
    fun `an item replayed immediately advances again`() = runTest {
        advancer()
        runCurrent()
        playThenEnd("a")

        playThenEnd("a")

        assertEquals(2, advanced)
    }
}
