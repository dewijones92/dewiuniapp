package com.dewijones92.totum.playback

/**
 * The player's measured throughput, published so the diagnostics listener can name it
 * when playback stalls.
 *
 * A hand-off rather than an injected dependency because the two live at opposite ends of
 * the session: the bandwidth meter is created with the player inside the service, and the
 * listener that reports stalls is attached by the controller in another process-local
 * component. Threading it through would mean widening several constructors to carry a
 * number that only diagnostics ever reads.
 */
public object PlaybackVitals {

    /** Set by the service when the player is built; null until then. */
    public var bitrateEstimate: (() -> Long)? = null

    /** Kilobits per second, or null when nothing has been measured yet. */
    public fun kbps(): Long? = bitrateEstimate?.invoke()?.takeIf { it > 0 }?.div(BITS_PER_KILOBIT)

    private const val BITS_PER_KILOBIT = 1_000L
}
