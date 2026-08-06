package com.dewijones92.totum.playback

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The buffers must fit in the heap the app actually gets.
 *
 * 0.1.346 died of `OutOfMemoryError` twice in 230ms on the playback thread (2026-08-06). Nothing
 * in the code said how much memory playback was allowed to take, so nothing could be wrong — and
 * Media3's defaults, which apply when you set none, are **125MB for a video track plus 12.5MB for
 * audio, and a further 137.5MB for the preload manager**. Against this app's 256MB heap those are
 * not compatible, and raising the buffer to four minutes (2026-08-01) is what made them reachable:
 * at Media3's default ~50 seconds the loader stopped long before the byte cap mattered.
 *
 * So the budget is now explicit, and this test is the thing that keeps it honest. It is arithmetic
 * rather than behaviour on purpose: the failure it guards against is somebody raising a buffer for
 * a good reason — the stall work did exactly that — without noticing the ceiling.
 */
class MemoryBudgetTest {

    /**
     * The SMALLEST heap this app runs on, not the phone's.
     *
     * A Pixel 7 gives an app 256MB without `largeHeap`; CI's API-35 emulator gives **192MB**,
     * measured on 2026-08-06 (`[memory] heap 40MB of 192MB`). Budgeting against the roomier
     * device would leave the tighter one unprotected, and the tighter one is where the tests run.
     */
    private val heapBytes = 192L * 1024 * 1024

    private val playback = BufferBudget.PLAYBACK_BYTES.toLong()
    private val preload = BufferBudget.PRELOAD_BYTES.toLong()

    /**
     * Half the heap, because media buffers are not the only tenant: Compose, artwork, the feed
     * caches, 1593 subscriptions and the embedded Python interpreter all live there too, and the
     * report that recorded the crash showed 55MB in use with nothing playing.
     */
    @Test
    fun `playback and preload together leave the rest of the app room to live`() {
        val together = playback + preload

        assertTrue(
            "media buffers may take at most half the heap; playback ${playback / MB}MB + preload " +
                "${preload / MB}MB = ${together / MB}MB of ${heapBytes / MB}MB",
            together <= heapBytes / 2,
        )
    }

    /** Four minutes of speech audio is the case the long buffer was raised for; it must still fit. */
    @Test
    fun `the four-minute buffer still fits for audio`() {
        val fourMinutesOfSpeech = BufferBudget.MAX_BUFFER_MS / MS_PER_SECOND *
            SPEECH_BITS_PER_SECOND / BITS_PER_BYTE

        assertTrue(
            "four minutes of ${SPEECH_BITS_PER_SECOND / 1000}kbps audio is " +
                "${fourMinutesOfSpeech / MB}MB and must fit in ${playback / MB}MB, or the stall " +
                "fix silently stopped working for listening",
            fourMinutesOfSpeech < playback,
        )
    }

    /** And the preload must actually hold the 30 seconds it claims, for video as well as audio. */
    @Test
    fun `the preload holds its thirty seconds of video`() {
        val thirtySecondsOf1080p = BufferBudget.PRELOAD_MS / MS_PER_SECOND *
            HD_BITS_PER_SECOND / BITS_PER_BYTE

        assertTrue(
            "30s of ${HD_BITS_PER_SECOND / 1_000_000}Mbps video is ${thirtySecondsOf1080p / MB}MB " +
                "and must fit in ${preload / MB}MB",
            thirtySecondsOf1080p < preload,
        )
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val MS_PER_SECOND = 1000L
        const val BITS_PER_BYTE = 8L

        /** A typical spoken-word podcast or an audio-only YouTube stream. */
        const val SPEECH_BITS_PER_SECOND = 128_000L

        /** 1080p at a realistic streaming bitrate. */
        const val HD_BITS_PER_SECOND = 5_000_000L
    }
}
