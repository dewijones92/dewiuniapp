package com.dewijones92.totum.ui.player

/**
 * The screen brightness the user set by gesture, and whether anything on screen still wants it.
 *
 * Dewi, 2026-08-05, on settings that must not move on their own: *"brightness … should not change
 * until I deliberately change them in the GUI"*, and on how far that should go for brightness
 * specifically — remember it for the session, do not persist it.
 *
 * Two separate things have to be true at once, and keeping them in one place is the point:
 *
 * - **The CHOICE outlives the window.** The window override has to be dropped whenever video goes
 *   away, or the queue and settings screens inherit a dimmed window. But the gesture state lives
 *   with the player composable, so dropping the override also forgot the choice — and on a queue of
 *   videos the brightness reset at every track change.
 * - **The window is shared, so it is released by the LAST stage, not the first.** Going fullscreen
 *   swaps one subtree for another, which disposes one stage and creates another. Both are correct
 *   on their own; the trouble is the order Compose runs them in. The incoming stage re-applied the
 *   brightness during *composition* and the outgoing one released it in its `onDispose`, during the
 *   *effects* phase afterwards — so the release always landed last and the screen dropped back to
 *   system brightness at every transition, for the rest of the session (Dewi, 2026-08-08:
 *   *"turned down when I go into full screen video"*, and it never came back).
 *
 * Counting stages fixes that by making the answer **independent of the order**: while any stage is
 * on screen the window shows the choice, whichever way round the two lifecycle calls happen to run.
 * A flag on either composable could not do this, because neither one knows about the other.
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

    /** How many video stages are on screen. Normally 0 or 1; briefly 2 across a fullscreen swap. */
    var stagesOnScreen: Int = 0
        private set

    /** Records a deliberate gesture. Values outside 0..1 are clamped rather than rejected. */
    fun choose(level: Float) {
        value = level.coerceIn(0f, 1f)
    }

    /** Whether there is a choice to re-apply when a video next appears. */
    val isSet: Boolean get() = value >= 0f

    /** What the window should be showing right now. */
    val windowBrightness: Float get() = if (stagesOnScreen > 0 && isSet) value else FOLLOW_SYSTEM

    /** A video stage appeared; returns the brightness the window should now show. */
    fun stageAppeared(): Float {
        stagesOnScreen++
        return windowBrightness
    }

    /**
     * A video stage went away; returns the brightness the window should now show.
     *
     * Floored at zero rather than trusted to balance: an unbalanced call would otherwise leave the
     * count negative forever and the override could never be re-applied — a far worse failure than
     * one stray release.
     */
    fun stageDisappeared(): Float {
        stagesOnScreen = (stagesOnScreen - 1).coerceAtLeast(0)
        return windowBrightness
    }

    /** Only for tests and a deliberate reset — never called when a video merely ends. */
    fun forget() {
        value = FOLLOW_SYSTEM
        stagesOnScreen = 0
    }
}
