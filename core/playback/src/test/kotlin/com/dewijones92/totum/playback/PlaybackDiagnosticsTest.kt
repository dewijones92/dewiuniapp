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
     * An item change ends the stall rather than carrying it across, so switching item
     * mid-buffer cannot attribute the wait to the next thing played.
     */
    @Test
    fun `changing item abandons an in-flight stall`() {
        diagnostics.onPlaybackStateChanged(Player.STATE_BUFFERING)
        clock = 3_000
        diagnostics.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        clock = 3_100
        diagnostics.onPlaybackStateChanged(Player.STATE_READY)

        assertEquals("1", Vitals.snapshot()["playback.stalls"])
        assertEquals(null, Vitals.snapshot()["playback.bufferingMs"])
    }

    @Test
    fun `transitions record why they happened`() {
        diagnostics.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)

        val messages = Breadcrumbs.snapshot().map { it.message }
        assertTrue("expected an auto transition in $messages", messages.any { "auto" in it })
    }
}
