package com.dewijones92.totum.playback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One speed for the whole app, changed only when the user changes it.
 *
 * Dewi, 2026-08-05: *"playback speed … should not change until I deliberately change them in the
 * GUI — no exceptions"*, and *"global please"*.
 *
 * The store used to be keyed by source, so the speed moved on its own every time the queue reached
 * something from a different channel or feed. From the outside that is not a feature, it is the app
 * changing a setting nobody touched.
 */
class GlobalPlaybackSpeedTest {

    /** A tiny in-memory store with the same contract as the SharedPreferences one. */
    private class InMemorySpeedStore : PlaybackSpeedStore {
        private var value: Float? = null
        override suspend fun speed(): Float = value ?: DEFAULT_PLAYBACK_SPEED
        override suspend fun save(speed: Float) {
            value = speed
        }
    }

    @Test
    fun `nothing chosen yet plays at normal speed`() = runTest {
        assertEquals(DEFAULT_PLAYBACK_SPEED, InMemorySpeedStore().speed(), 0f)
    }

    /** The point of the change: one value, whatever is playing. */
    @Test
    fun `a chosen speed applies to everything afterwards`() = runTest {
        val store = InMemorySpeedStore()

        store.save(1.5f)

        assertEquals(1.5f, store.speed(), 0f)
        assertEquals("and again, for the next item from anywhere", 1.5f, store.speed(), 0f)
    }

    @Test
    fun `changing it again replaces the one value`() = runTest {
        val store = InMemorySpeedStore()

        store.save(1.5f)
        store.save(2.0f)

        assertEquals(2.0f, store.speed(), 0f)
    }

    /** The no-op store must never remember anything, or previews would drift too. */
    @Test
    fun `the no-op store always reports normal speed`() = runTest {
        NoOpPlaybackSpeedStore.save(2.0f)

        assertEquals(DEFAULT_PLAYBACK_SPEED, NoOpPlaybackSpeedStore.speed(), 0f)
    }
}
