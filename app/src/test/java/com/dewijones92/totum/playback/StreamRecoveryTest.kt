package com.dewijones92.totum.playback

import com.dewijones92.totum.domain.MediaItemId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamRecoveryTest {

    private val failures = MutableSharedFlow<StreamFailure>(extraBufferCapacity = 8)
    private val replayedFrom = mutableListOf<Long>()
    private var movedOn = 0

    /** Completed by the test when it wants "the network came back". */
    private val networkBack = CompletableDeferred<Unit>()
    private var waitedForNetwork = 0

    /**
     * Backoff defaults to zero here so the budget and reason tests stay about the decision
     * rather than the clock; the wait itself has its own test below.
     */
    /** Times the next item was resolved ahead — should be once per failing item, not per retry. */
    private var prefetched = 0

    private fun TestScope.recovery(maxAttempts: Int = 3, backoffMs: Long = 0): StreamRecovery =
        StreamRecovery(
            failures = failures,
            replay = { at ->
                replayedFrom += at
                true
            },
            moveOn = {
                movedOn++
                true
            },
            prefetchNext = { prefetched++ },
            awaitNetwork = {
                waitedForNetwork++
                networkBack.await()
            },
            scope = backgroundScope,
            maxAttempts = maxAttempts,
            backoffMs = backoffMs,
        ).also { it.start() }

    @Test
    fun `re-resolves from the position the stream died at`() = runTest {
        recovery()
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        failures.emit(expired("a", at = 1_261_405))
        runCurrent()

        assertEquals(listOf(1_261_405L), replayedFrom)
    }

    @Test
    fun `stops after the retry budget, so a dead video cannot loop forever`() = runTest {
        recovery(maxAttempts = 3)
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        repeat(6) { failures.emit(expired("a", at = 500)) }
        runCurrent()

        assertEquals(3, replayedFrom.size)
    }

    @Test
    fun `a different item gets its own budget`() = runTest {
        recovery(maxAttempts = 1)
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        failures.emit(expired("a", at = 500))
        failures.emit(expired("a", at = 500))
        failures.emit(expired("b", at = 500))
        runCurrent()

        assertEquals(2, replayedFrom.size)
    }

    /** A long listen crosses more than one lease; each expiry is its own failure. */
    @Test
    fun `real progress since the last failure earns a fresh budget`() = runTest {
        recovery(maxAttempts = 1)
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses
        failures.emit(expired("a", at = 10_000))
        failures.emit(expired("a", at = 11_000)) // no real progress
        failures.emit(expired("a", at = 600_000)) // ten minutes on
        runCurrent()

        assertEquals(listOf(10_000L, 600_000L), replayedFrom)
    }

    @Test
    fun `a replay that cannot start is survivable`() = runTest {
        StreamRecovery(
            failures = failures,
            replay = { false },
            moveOn = {
                movedOn++
                true
            },
            awaitNetwork = {},
            scope = backgroundScope,
        ).start()
        runCurrent()
        failures.emit(expired("a", at = 1))
        runCurrent()

        assertTrue("should not have thrown", true)
    }

    /**
     * A real report had the player dead on one item with 58 more behind it, going nowhere.
     * Giving up on the item must not mean giving up on the queue.
     */
    @Test
    fun `once the budget is spent it moves to the next item`() = runTest {
        recovery(maxAttempts = 1)
        runCurrent()
        failures.emit(expired("a", at = 500))
        failures.emit(expired("a", at = 500))
        runCurrent()

        assertEquals(1, replayedFrom.size)
        assertEquals(1, movedOn)
    }

    @Test
    fun `it does not move on while it still has attempts left`() = runTest {
        recovery(maxAttempts = 3)
        runCurrent()
        failures.emit(expired("a", at = 500))
        runCurrent()

        assertEquals(0, movedOn)
    }

    /**
     * The tunnel case, measured on the emulator 2026-07-31: HTTPS black-holed mid-playback,
     * the player left at exactly 517805ms in IDLE, and the rule then removed. Before this it
     * sat there for over three minutes with full connectivity and would never have resumed.
     */
    @Test
    fun `a stream that went unreachable resumes when the network comes back`() = runTest {
        recovery()
        runCurrent()
        failures.emit(unreachable(at = 517_805))
        runCurrent()

        assertEquals("must not replay into a dead network", emptyList<Long>(), replayedFrom)
        assertEquals(1, waitedForNetwork)

        networkBack.complete(Unit)
        runCurrent()

        assertEquals("resumes exactly where it stopped", listOf(517_805L), replayedFrom)
    }

    /**
     * The whole reason the reason exists. Retrying into a dead network would spend the
     * budget on connections that never had a chance, and the item would be skipped for
     * having been in a tunnel.
     */
    @Test
    fun `an expiry is never made to wait for the network`() = runTest {
        recovery()
        runCurrent()
        failures.emit(expired("a", at = 900))
        runCurrent()

        assertEquals(listOf(900L), replayedFrom)
        assertEquals("an expiry means the network is fine", 0, waitedForNetwork)
    }

    /** An item broken in a way no network fixes must still eventually free the queue. */
    @Test
    fun `unreachable still respects the retry budget`() = runTest {
        recovery(maxAttempts = 1)
        runCurrent()
        networkBack.complete(Unit)
        failures.emit(unreachable(at = 500))
        failures.emit(unreachable(at = 500))
        runCurrent()

        assertEquals(1, replayedFrom.size)
        assertEquals(1, movedOn)
    }

    /**
     * Found on the emulator 2026-07-31: with packets dropped while Android still reported a
     * validated network, the entire three-attempt budget was spent in 56 MILLISECONDS and
     * the item skipped — because each replay failed the instant it was tried. Weak signal
     * and captive portals look exactly like that, so the guard against a dead item was
     * skipping live ones.
     */
    @Test
    fun `retries are spaced out, so a fast-failing network cannot burn the budget instantly`() = runTest {
        recovery(maxAttempts = 3, backoffMs = 2_000)
        runCurrent()
        repeat(3) { failures.emit(expired("a", at = 500)) }
        runCurrent()

        assertEquals("the first attempt is immediate", 1, replayedFrom.size)

        advanceTimeBy(2_001)
        assertEquals("the second waits", 2, replayedFrom.size)

        advanceTimeBy(4_001)
        assertEquals("and the third waits longer", 3, replayedFrom.size)
    }

    private fun expired(id: String, at: Long) =
        StreamFailure(MediaItemId(id), positionMs = at, reason = StreamFailure.Reason.Expired)

    private fun unreachable(at: Long) =
        StreamFailure(MediaItemId("a"), positionMs = at, reason = StreamFailure.Reason.Unreachable)

    /**
     * The next item is resolved WHILE the retries run, not after them.
     *
     * Report 0.1.277 measured the cost of doing it afterwards: a stream failed, three recoveries
     * took 22 seconds, and only then did the next item start a 25-second extraction — 58 seconds
     * of silence from the first failure to sound, 28 of them after the app had already given up.
     * Overlapping the two costs nothing when recovery works, because the resolved result just
     * sits in the cache.
     */
    @Test
    fun `the next item starts resolving on the first failure`() = runTest {
        recovery()
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses

        failures.emit(expired("a", at = 1_000))
        runCurrent()

        assertEquals("resolving next should begin immediately, not after the retries", 1, prefetched)
    }

    /**
     * Once per failing item, not once per retry. Three retries firing three extractions of the
     * same video would put 75 seconds of work on a phone to save 25.
     */
    @Test
    fun `retrying the same item does not re-resolve the next one each time`() = runTest {
        recovery()
        runCurrent() // let the collector subscribe; a SharedFlow drops what it misses

        repeat(3) {
            failures.emit(expired("a", at = 1_000))
            runCurrent()
        }

        assertEquals(1, prefetched)
    }
}
