package com.dewijones92.totum.playback

/**
 * How much media the app holds, in time and in bytes — the whole buffering policy, in one place.
 *
 * It is one place because the two halves must be read together and were not. The durations were
 * raised to four minutes to stop stalling on a fast connection (report 0.1.289: 4.5s of stalling
 * across 16 minutes at 57-184 Mbps), and nothing said what that cost in memory. Media3's answer
 * when you set no byte limit is **125MB for a video track plus 12.5MB for audio, and another
 * 137.5MB for the preload manager** — more, together, than this app's 256MB heap. 0.1.346 then
 * died of `OutOfMemoryError` twice in 230ms on the playback thread (2026-08-06).
 *
 * So: keep the generous duration, which is what fixed the stalls, and add the byte ceiling that
 * makes it safe. `MemoryBudgetTest` holds the arithmetic to the heap, so raising one of these for
 * a good reason cannot quietly break the other.
 */
internal object BufferBudget {

    /** Start loading again below this much buffered. */
    const val MIN_BUFFER_MS = 30_000

    /**
     * Buffer minutes ahead, not Media3's ~50 seconds — you cannot stall for bandwidth at 184
     * Mbps, and the default simply stops fetching once it is ~50s ahead, so a hiccup empties a
     * buffer that had no business being that small.
     *
     * Bounded at four minutes rather than "the whole item": filling a queue of long items to the
     * end would be a download, and there is a button for that.
     */
    const val MAX_BUFFER_MS = 240_000

    /** A little behind too, so a small scrub back does not refetch. */
    const val BACK_BUFFER_MS = 30_000

    /**
     * What the player may hold on the heap: 64MB of 256MB.
     *
     * Chosen so the four minutes above still work where it matters and cannot cost the process.
     * Four minutes of 128kbps speech is 3.8MB, so listening — most of how this app is used —
     * still buffers the full four minutes. 1080p video at 5Mbps reaches ~100 seconds, still twice
     * Media3's default, and stops there rather than climbing towards the ceiling.
     */
    const val PLAYBACK_BYTES = 64 * 1024 * 1024

    /** How much of what is coming next to hold, so a track change is not a wait. */
    const val PRELOAD_MS = 30_000L

    /** What the preloader may hold: 30s of 1080p video is ~19MB, of audio ~1MB. */
    const val PRELOAD_BYTES = 24 * 1024 * 1024
}
