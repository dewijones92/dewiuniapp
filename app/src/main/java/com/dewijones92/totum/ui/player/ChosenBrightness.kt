package com.dewijones92.totum.ui.player

/**
 * The screen brightness the user set by gesture, kept for the sitting.
 *
 * Dewi, 2026-08-05, on settings that must not move on their own: *"brightness … should not change
 * until I deliberately change them in the GUI"*, and on how far that should go for brightness
 * specifically — remember it for the session, do not persist it.
 *
 * The window override has to be dropped whenever video goes away, or the queue and settings screens
 * inherit a dimmed window. But the gesture state lives with the player composable, so dropping the
 * override also forgot the choice — and on a queue of videos the brightness reset at every track
 * change. Holding the number here separates the two: the WINDOW is released, the CHOICE is not.
 *
 * Not persisted, deliberately. A brightness set weeks ago applying to a video today would be the
 * same surprise in the other direction.
 */
internal object ChosenBrightness {

    /** Negative means "follow the system", Android's own convention for no override. */
    internal const val FOLLOW_SYSTEM: Float = -1f

    /** The chosen level, or [FOLLOW_SYSTEM] if the user has not set one this session. */
    var value: Float = FOLLOW_SYSTEM
        private set

    /** Records a deliberate gesture. Values outside 0..1 are clamped rather than rejected. */
    fun choose(level: Float) {
        value = level.coerceIn(0f, 1f)
    }

    /** Whether there is a choice to re-apply when a video next appears. */
    val isSet: Boolean get() = value >= 0f

    /** Only for tests and a deliberate reset — never called when a video merely ends. */
    fun forget() {
        value = FOLLOW_SYSTEM
    }
}
