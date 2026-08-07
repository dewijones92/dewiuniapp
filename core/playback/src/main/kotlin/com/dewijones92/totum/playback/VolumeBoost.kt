package com.dewijones92.totum.playback

import kotlin.math.pow

/**
 * How much to lift quiet audio, as decibels of makeup gain.
 *
 * Applied by [LoudnessBoost] in the sink's own processing chain — compressed and limited, not a flat
 * gain. That change is what let these numbers grow: a bare gain clips, so past about +12 dB more of
 * it buys distortion rather than volume, which is where the old ceiling came from.
 *
 * Dewi, 2026-08-07: *"it isnt strong enough ... so I can hear quiet podcasts"*. HIGH went from +12 to
 * +20 dB and [MAX] is new at +30, which is a 4x and then a 10x change in amplitude over the old
 * ceiling. Deliberately a change to what the existing names MEAN rather than only an addition: HIGH
 * should be enough to rescue a quiet podcast, and it was not.
 *
 * Levels rather than a slider: the useful range is a handful of steps, and a named step is easier to
 * reason about than a decibel figure.
 */
public enum class VolumeBoost(public val decibels: Int) {
    OFF(0),
    LOW(LOW_DB),
    MEDIUM(MEDIUM_DB),
    HIGH(HIGH_DB),

    /**
     * As loud as speech can be made without it sounding broken.
     *
     * For the recordings that defeat everything else — a phone-mic interview, an old episode
     * mastered far too quietly. It leans hard on the limiter, so it is noticeably compressed: even,
     * close, and unmistakably processed. That is the trade, and it is worth it when the alternative
     * is not being able to hear the words.
     */
    MAX(MAX_DB),
    ;

    /** The gain as a linear multiplier, which is what the sample maths wants. */
    internal val makeupGain: Float get() = 10f.pow(decibels / DECIBELS_PER_DECADE)
}

private const val LOW_DB = 6
private const val MEDIUM_DB = 12
private const val HIGH_DB = 20
private const val MAX_DB = 30

/** 20 dB is a factor of ten in amplitude. */
private const val DECIBELS_PER_DECADE = 20f

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
