package com.dewijones92.totum.playback

import androidx.media3.common.Player
import com.dewijones92.totum.common.Breadcrumbs
import com.dewijones92.totum.common.Vitals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the timing logic, which is the part with actual behaviour — the error and
 * transition hooks are straight pass-throughs to [com.dewijones92.totum.common.Diag].
 */
class PlaybackDiagnosticsTest {

    private var clock = 0L

    // No player: every field it would read is optional in the messages, and a real
    // Media3 Player is a very large interface to fake for no gain here.
    private val diagnostics = PlaybackDiagnostics(player = { null }, now = { clock })

    @Before
    fun reset() {
        Vitals.clear()
        Breadcrumbs.clear()
    }

    @After
    fun tidy() {
        Vitals.clear()
        Breadcrumbs.clear()
    }

    @Test
    fun `a stall is counted and its duration recorded`() {
        diagnostics.onPlaybackStateChanged(Player.STATE_BUFFERING)
        clock = 1500
        diagnostics.onPlaybackStateChanged(Player.STATE_READY)

        assertEquals("1", Vitals.snapshot()["playback.stalls"])
        assertEquals("1500", Vitals.snapshot()["playback.bufferingMs"])
    }

    @Test
    fun `repeated stalls accumulate, which is what makes them visible`() {
        listOf(0L to 200L, 5_000L to 5_300L, 9_000L to 9_100L).forEach { (start, ready) ->
            clock = start
            diagnostics.onPlaybackStateChanged(Player.STATE_BUFFERING)
            clock = ready
            diagnostics.onPlaybackStateChanged(Player.STATE_READY)
        }

        assertEquals("3", Vitals.snapshot()["playback.stalls"])
        assertEquals("600", Vitals.snapshot()["playback.bufferingMs"])
    }

    /** Becoming ready without having stalled must not invent a duration. */
    @Test
    fun `ready with no preceding stall records nothing`() {
        clock = 4_000
        diagnostics.onPlaybackStateChanged(Player.STATE_READY)

        assertEquals(null, Vitals.snapshot()["playback.bufferingMs"])
    }

    /**
     * An item change CLOSES the stall — it is not carried across, so the wait can never be
     * attributed to the next thing played. That was always the point of this test.
     *
     * What changed is where the closed stall goes. It used to be discarded, so the 3 seconds
     * here counted for nothing; now it is charged to the item that actually suffered it. Report
     * 0.1.332 is why: a 136-second freeze ended by Dewi pressing play again produced a transition,
     * so the single worst stall in the session was erased by the line meant to prevent
     * mis-attribution. Closing it and counting it does both jobs.
     */
    @Test
    fun `changing item closes an in-flight stall and charges it to that item`() {
        diagnostics.onPlaybackStateChanged(Player.STATE_BUFFERING)
        clock = 3_000
        diagnostics.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        clock = 3_100
        diagnostics.onPlaybackStateChanged(Player.STATE_READY)

        assertEquals("1", Vitals.snapshot()["playback.stalls"])
        // The 3s it waited, and NOT the 100ms after the switch — the stall did not survive it.
        assertEquals("3000", Vitals.snapshot()["playback.bufferingMs"])
    }

    @Test
    fun `transitions record why they happened`() {
        diagnostics.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        val messages = Breadcrumbs.snapshot().map { it.message }
        assertTrue("expected an auto transition in $messages", messages.any { "auto" in it })
    }

    /**
     * The stall that never recovers is the one that matters, and it used to count for nothing.
     *
     * Report 0.1.332 recorded `bufferingMs = 1370` for a session containing a 136-second freeze,
     * because the total was only written on STATE_READY and every other exit discarded it. The
     * spinner Dewi escaped by pressing play again contributed zero to the one metric named after
     * it — which is why "we have lots of buffering issues" never showed up in the numbers.
     */
    @Test
    fun `buffering abandoned by moving to another item is still counted`() {
        diagnostics.onPlaybackStateChanged(Player.STATE_BUFFERING)
        clock = 136_000
        diagnostics.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        assertEquals("136000", Vitals.snapshot()["playback.bufferingMs"])
        assertEquals("136000", Vitals.snapshot()["playback.abandonedBufferingMs"])
    }

    /**
     * Recovered and abandoned are counted apart as well as together: a 400ms re-buffer and a
     * freeze someone gave up on are not the same event, and one number cannot say which happened.
     */
    @Test
    fun `a stall that recovers is not counted as abandoned`() {
        diagnostics.onPlaybackStateChanged(Player.STATE_BUFFERING)
        clock = 400
        diagnostics.onPlaybackStateChanged(Player.STATE_READY)

        assertEquals("400", Vitals.snapshot()["playback.bufferingMs"])
        assertEquals(null, Vitals.snapshot()["playback.abandonedBufferingMs"])
    }

    @Test
    fun `buffering ended by the player going idle is counted`() {
        diagnostics.onPlaybackStateChanged(Player.STATE_BUFFERING)
        clock = 9_000
        diagnostics.onPlaybackStateChanged(Player.STATE_IDLE)

        assertEquals("9000", Vitals.snapshot()["playback.abandonedBufferingMs"])
    }

    /** A transition with nothing buffering must not invent a zero-length stall. */
    @Test
    fun `a transition while playing normally counts no buffering`() {
        diagnostics.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        assertEquals(null, Vitals.snapshot()["playback.bufferingMs"])
    }
}
