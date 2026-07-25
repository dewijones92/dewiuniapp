package com.dewijones92.totum.domain

/**
 * How far through an item the listener/viewer is — the same three states for a podcast
 * episode and a video, so any list can say what it is without knowing its pillar.
 *
 * This exists because "played" used to be unrepresentable: progress rows for finished
 * items were deleted, making a finished item indistinguishable from one never started.
 * Restarting from the beginning is now a property of *playback* (start at 0 when
 * completed) rather than of *storage*.
 */
public sealed interface PlayState {

    /** Never started, or not far enough in to be worth remembering. */
    public data object Unplayed : PlayState

    /** Started and not finished. [fraction] is null while the duration is unknown. */
    public data class InProgress(val positionMs: Long, val durationMs: Long?) : PlayState {
        init {
            require(positionMs >= 0) { "positionMs must not be negative" }
        }

        public val fraction: Float?
            get() = durationMs
                ?.takeIf { it > 0 }
                ?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** Reached the end, or marked played by hand. */
    public data object Played : PlayState

    public val isPlayed: Boolean get() = this is Played
}
