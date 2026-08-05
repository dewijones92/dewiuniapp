package com.dewijones92.totum.playback

/**
 * How much to lift quiet audio. A platform `LoudnessEnhancer` sits on the player's
 * audio session and applies the gain, so it covers both pillars from one place.
 *
 * Levels rather than a slider: the useful range is small, and a named step is easier
 * to reason about than a decibel figure.
 */
public enum class VolumeBoost(public val gainMillibels: Int) {
    OFF(NO_GAIN),
    LOW(LOW_GAIN_MB),
    MEDIUM(MEDIUM_GAIN_MB),
    HIGH(HIGH_GAIN_MB),
}

// Millibels (a hundredth of a decibel) — the unit LoudnessEnhancer takes. +3 dB is a
// gentle lift, +12 dB rescues genuinely quiet speech without obvious distortion.
private const val NO_GAIN = 0
private const val LOW_GAIN_MB = 300
private const val MEDIUM_GAIN_MB = 700
private const val HIGH_GAIN_MB = 1200

/**
 * Remembers ONE boost for the whole app, mirroring [PlaybackSpeedStore].
 *
 * Dewi, 2026-08-05: *"things like playback speed, brightness, volume booster settings should not
 * change until I deliberately change them in the GUI — no exceptions"*.
 *
 * It was keyed by `SourceId`, on the reasoning that a quietly recorded podcast could stay boosted
 * without shouting at you everywhere else. Same defensible idea as per-source speed, and the same
 * problem: the level then changes on its own every time the queue reaches a different source, which
 * from the outside is the app altering a setting nobody touched. One value, moved only by [save],
 * and [save] is only ever called from the boost control.
 */
public interface VolumeBoostStore {

    /** The remembered boost, or [VolumeBoost.OFF] if the user has never chosen one. */
    public suspend fun boost(): VolumeBoost

    /** Records [boost] as the chosen level. Called only from a deliberate change in the UI. */
    public suspend fun save(boost: VolumeBoost)
}

/** Default store that remembers nothing — everything plays unboosted. */
public object NoOpVolumeBoostStore : VolumeBoostStore {
    override suspend fun boost(): VolumeBoost = VolumeBoost.OFF
    override suspend fun save(boost: VolumeBoost): Unit = Unit
}
