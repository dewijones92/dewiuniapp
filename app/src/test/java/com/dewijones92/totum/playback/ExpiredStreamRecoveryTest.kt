package com.dewijones92.totum.playback

import com.dewijones92.totum.domain.MediaItemId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiredStreamRecoveryTest {

    private val failures = MutableSharedFlow<StreamFailure>(extraBufferCapacity = 8)
    private val replayedFrom = mutableListOf<Long>()

    private fun TestScope.recovery(maxAttempts: Int = 3): ExpiredStreamRecovery =
        ExpiredStreamRecovery(
            failures = failures,
            replay = { at ->
                replayedFrom += at
                true
            },
            scope = backgroundScope,
            maxAttempts = maxAttempts,
        ).also { it.start() }

    @Test
    fun `re-resolves from the position the stream died at`() = runTest {
        recovery()
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        failures.emit(StreamFailure(MediaItemId("a"), positionMs = 1_261_405))
        runCurrent()

        assertEquals(listOf(1_261_405L), replayedFrom)
    }

    @Test
    fun `stops after the retry budget, so a dead video cannot loop forever`() = runTest {
        recovery(maxAttempts = 3)
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        repeat(6) { failures.emit(StreamFailure(MediaItemId("a"), positionMs = 500)) }
        runCurrent()

        assertEquals(3, replayedFrom.size)
    }

    @Test
    fun `a different item gets its own budget`() = runTest {
        recovery(maxAttempts = 1)
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        failures.emit(StreamFailure(MediaItemId("a"), positionMs = 500))
        failures.emit(StreamFailure(MediaItemId("a"), positionMs = 500))
        failures.emit(StreamFailure(MediaItemId("b"), positionMs = 500))
        runCurrent()

        assertEquals(2, replayedFrom.size)
    }

    /** A long listen crosses more than one lease; each expiry is its own failure. */
    @Test
    fun `real progress since the last failure earns a fresh budget`() = runTest {
        recovery(maxAttempts = 1)
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        failures.emit(StreamFailure(MediaItemId("a"), positionMs = 10_000))
        failures.emit(StreamFailure(MediaItemId("a"), positionMs = 11_000)) // no real progress
        failures.emit(StreamFailure(MediaItemId("a"), positionMs = 600_000)) // ten minutes on
        runCurrent()

        assertEquals(listOf(10_000L, 600_000L), replayedFrom)
    }

    @Test
    fun `a replay that cannot start is survivable`() = runTest {
        ExpiredStreamRecovery(
            failures = failures,
            replay = { false },
            scope = backgroundScope,
        ).start()
        runCurrent()
        failures.emit(StreamFailure(MediaItemId("a"), positionMs = 1))
        runCurrent()

        assertTrue("should not have thrown", true)
    }
}
