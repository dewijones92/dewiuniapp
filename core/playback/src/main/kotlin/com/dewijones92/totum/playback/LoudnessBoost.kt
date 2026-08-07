package com.dewijones92.totum.playback

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Makes quiet speech audible: gain, compression and limiting on 16-bit PCM.
 *
 * Dewi, 2026-08-07: *"there is already some sort of volume booster in the app but it isnt strong
 * enough, can we do a stronger one, so I can hear quiet podcasts"*.
 *
 * **Why the old one could not be made stronger by turning it up.** It was a platform
 * `LoudnessEnhancer` applying one flat gain, capped here at +12 dB. A flat gain has two problems that
 * more of it makes worse: it clips, so past a point extra gain buys distortion rather than volume;
 * and it cannot help the quiet *passages within* an episode, because it treats a shouted intro and a
 * mumbled answer identically. A podcast recorded 20 dB quiet needs more than 12, and the parts you
 * actually cannot hear are the quietest parts of an already-quiet recording.
 *
 * **What makes speech audible is compression, not gain.** Follow the signal's envelope, and pull the
 * gain back only when a peak would otherwise clip. Quiet passages then get the full lift while loud
 * ones stay where they are, which is both louder *and* more even — the thing every broadcaster does
 * to speech. Limiting is what lets the makeup gain go to +30 dB without the result being a fuzz.
 *
 * Three details that separate this from a naive limiter, each of which sounds bad if skipped:
 *
 * - **The gain is smoothed, not switched.** Recomputing it per sample and applying it immediately
 *   modulates the waveform at audio rate, which is heard as distortion rather than as level control.
 * - **Hiss is not amplified.** +30 dB on room noise between sentences is horrible, so below
 *   [NOISE_FLOOR] the gain tapers away. Silence stays silent.
 * - **One envelope for all channels.** Per-channel gain would move a stereo image around as one side
 *   happened to peak.
 *
 * Pure arithmetic on a `ShortArray`, deliberately: no Android, no platform effect, so it behaves the
 * same on every device and — the part that matters here — the maths can be proven on the JVM.
 */
internal class LoudnessBoost(sampleRate: Int) {

    /** How hard to push. Set from the UI; changing it mid-stream is smoothed like everything else. */
    var level: VolumeBoost = VolumeBoost.OFF

    /** Follows the signal's amplitude, 0..1. */
    private var envelope = 0f

    /** The gain actually being applied, moved gradually towards its target. */
    private var applied = 1f

    private val attack = coefficientFor(ATTACK_MS, sampleRate)
    private val release = coefficientFor(RELEASE_MS, sampleRate)
    private val gainGlide = coefficientFor(GAIN_GLIDE_MS, sampleRate)

    /**
     * Boosts [count] samples of [samples] in place.
     *
     * In place because this sits in the sink's processing chain, where a copy per buffer is a copy
     * per few milliseconds of audio for the whole of playback.
     */
    fun process(samples: ShortArray, count: Int) {
        val makeup = level.makeupGain
        // OFF is bit-exact passthrough, not a gain of one: a multiply-and-round on every sample of
        // every stream forever, to change nothing, is worth skipping.
        if (level == VolumeBoost.OFF) return

        for (i in 0 until count) {
            val sample = samples[i] / FULL_SCALE
            val magnitude = abs(sample)

            // Peak follower: fast up so a transient is caught before it clips, slow down so the gain
            // does not lurch back between syllables.
            envelope += (magnitude - envelope) * if (magnitude > envelope) attack else release

            // What gain would keep this envelope under the ceiling — never more than the makeup.
            val safe = if (envelope > EPSILON) min(makeup, CEILING / envelope) else makeup
            // ...and taper it away for anything at hiss level, so the gaps between sentences do not
            // come up as a roar.
            val target = safe * floorTaper(envelope)

            applied += (target - applied) * gainGlide
            samples[i] = clamp(sample * applied)
        }
    }

    /**
     * 1 for real signal, falling steeply to 0 for anything at or below the noise floor.
     *
     * A curve rather than a gate: something that opened and shut would chop the start of every quiet
     * word, which is exactly the audio this exists to rescue.
     */
    private fun floorTaper(envelope: Float): Float {
        val ratio = (envelope / NOISE_FLOOR).coerceIn(0f, 1f)
        // CUBED, not proportional. A proportional taper still let hiss through at a quarter of the
        // makeup gain, which at +30 dB measured as an ELEVENFOLD lift of the noise floor — audibly a
        // roar between sentences, and the first thing that would have made this unusable. Cubing it
        // turns the region below the floor into a gentle downward expander instead: noise comes out
        // slightly quieter than it went in, while anything at speech level is already clamped to 1
        // and gets the full lift.
        return ratio * ratio * ratio
    }

    private fun clamp(value: Float): Short {
        val scaled = value * FULL_SCALE
        return when {
            scaled >= MAX_SAMPLE -> Short.MAX_VALUE
            scaled <= MIN_SAMPLE -> Short.MIN_VALUE
            else -> scaled.toInt().toShort()
        }
    }

    internal companion object {
        /**
         * A one-pole smoothing coefficient for a given time constant.
         *
         * Per sample rather than per buffer: buffer sizes vary with the device and the format, and a
         * smoothing rate that changed with them would make the boost sound different on each.
         */
        fun coefficientFor(milliseconds: Float, sampleRate: Int): Float {
            if (sampleRate <= 0 || milliseconds <= 0f) return 1f
            return 1f - exp(-1f / (milliseconds / MILLIS_PER_SECOND * sampleRate))
        }

        /** Fast enough to catch a transient before it clips. */
        private const val ATTACK_MS = 5f

        /** Slow enough not to pump between syllables; short enough to follow a change of speaker. */
        private const val RELEASE_MS = 300f

        /** The gain itself glides, so level control is never heard as distortion. */
        private const val GAIN_GLIDE_MS = 30f

        /** Just under full scale, so rounding cannot take a limited peak over it. */
        private const val CEILING = 0.95f

        /**
         * Roughly -45 dBFS. Above this is signal worth lifting; below it is room noise, tape hiss and
         * the gaps between sentences, which at +30 dB would be unbearable.
         */
        private const val NOISE_FLOOR = 0.0056f

        private const val FULL_SCALE = 32_768f
        private const val MAX_SAMPLE = 32_767f
        private const val MIN_SAMPLE = -32_768f
        private const val MILLIS_PER_SECOND = 1_000f
        private const val EPSILON = 1e-6f
    }
}
