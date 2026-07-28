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
    private var suppressed = false
    private var queueHasNext = true

    private fun TestScope.advancer() = AutoAdvancer(
        states = states,
        advance = {
            advanced++
            queueHasNext
        },
        whenQueueEmpty = { fellBackToRelated++ },
        isEnabled = { enabled },
        isSuppressed = { suppressed },
        scope = backgroundScope,
    ).also { it.start() }

    @Test
    fun `an ended item advances the queue`() = runTest {
        advancer()
        runCurrent()
        states.value = state("a", ended = true)
        runCurrent()

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
        states.value = state("a", ended = true)
        runCurrent()

        assertEquals(0, advanced)
    }

    /** The shorts reel pages itself; advancing over it would fight the user's swipe. */
    @Test
    fun `a suppressing screen means no advance`() = runTest {
        suppressed = true
        advancer()
        runCurrent()
        states.value = state("a", ended = true)
        runCurrent()

        assertEquals(0, advanced)
    }

    @Test
    fun `an empty queue falls back to a related video`() = runTest {
        queueHasNext = false
        advancer()
        runCurrent()
        states.value = state("a", ended = true)
        runCurrent()

        assertEquals(1, advanced)
        assertEquals(1, fellBackToRelated)
    }

    @Test
    fun `each item gets its own end`() = runTest {
        advancer()
        runCurrent()
        states.value = state("a", ended = true)
        runCurrent()
        states.value = state("b", ended = false)
        runCurrent()
        states.value = state("b", ended = true)
        runCurrent()

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
        states.value = state("a", ended = true)
        runCurrent()

        enabled = true
        runCurrent()

        assertEquals(0, advanced)
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
}
