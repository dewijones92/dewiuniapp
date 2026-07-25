package com.dewijones92.uniapp.playback

import com.dewijones92.uniapp.domain.SourceId

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
 * Remembers the boost chosen per source, mirroring [PlaybackSpeedStore]: a quietly
 * recorded podcast stays boosted without shouting at you everywhere else. Keyed by
 * [SourceId] so it carries across every episode or upload from that source.
 */
public interface VolumeBoostStore {

    /** The remembered boost for [source], or [VolumeBoost.OFF] if none. */
    public suspend fun boostFor(source: SourceId): VolumeBoost

    /** Records [boost] as the preferred level for [source]. */
    public suspend fun save(source: SourceId, boost: VolumeBoost)
}

/** Default store that remembers nothing — everything plays unboosted. */
public object NoOpVolumeBoostStore : VolumeBoostStore {
    override suspend fun boostFor(source: SourceId): VolumeBoost = VolumeBoost.OFF
    override suspend fun save(source: SourceId, boost: VolumeBoost): Unit = Unit
}
