package com.dewijones92.totum.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Whether a quiet podcast actually becomes audible, and whether a loud one survives it.
 *
 * Dewi, 2026-08-07: *"it isnt strong enough, can we do a stronger one, so I can hear quiet
 * podcasts"*. The old boost was a platform `LoudnessEnhancer` at +12 dB — a flat gain, which clips,
 * which is why it could not simply be turned up.
 *
 * These are the claims that make the new one worth having, and each is a way the naive version fails:
 * quiet speech comes up a *lot*, loud speech does not clip, hiss does not come up with it, and the
 * gain moves smoothly enough not to be heard as distortion. All arithmetic on a `ShortArray`, so it
 * is provable here rather than by listening on a device.
 */
class LoudnessBoostTest {

    private val rate = 44_100

    /** A sine at [amplitude] of full scale — a stand-in for steady speech at a given level. */
    private fun tone(amplitude: Float, seconds: Float = 1f, hz: Float = 200f): ShortArray {
        val count = (rate * seconds).toInt()
        return ShortArray(count) { i ->
            (sin(2 * PI * hz * i / rate) * amplitude * FULL_SCALE).toInt().toShort()
        }
    }

    private fun ShortArray.rms(from: Int = 0): Double {
        if (from >= size) return 0.0
        var sum = 0.0
        for (i in from until size) sum += toDouble(this[i]) * toDouble(this[i])
        return sqrt(sum / (size - from))
    }

    private fun toDouble(sample: Short) = sample.toDouble()

    private fun ShortArray.peak(): Int = maxOf(0) { abs(it.toInt()) }

    private inline fun ShortArray.maxOf(initial: Int, selector: (Short) -> Int): Int {
        var best = initial
        forEach { best = kotlin.math.max(best, selector(it)) }
        return best
    }

    private fun boosted(input: ShortArray, level: VolumeBoost): ShortArray {
        val copy = input.copyOf()
        LoudnessBoost(rate).apply { this.level = level }.process(copy, copy.size)
        return copy
    }

    /** THE POINT. Speech recorded far too quietly has to become properly audible. */
    @Test
    fun `a very quiet recording is lifted a long way`() {
        val quiet = tone(QUIET)
        val loud = boosted(quiet, VolumeBoost.MAX)

        // Measured after the smoothing has settled, since the first few ms are the gain gliding up.
        val before = quiet.rms(SETTLED)
        val after = loud.rms(SETTLED)
        assertTrue(
            "expected a large lift; went from ${before.toInt()} to ${after.toInt()}",
            after > before * MIN_MAX_LIFT,
        )
    }

    /** And HIGH — the level that was not enough before — has to be a real improvement on +12 dB. */
    @Test
    fun `high is substantially louder than the old ceiling was`() {
        val quiet = tone(QUIET)

        val high = boosted(quiet, VolumeBoost.HIGH).rms(SETTLED)
        val medium = boosted(quiet, VolumeBoost.MEDIUM).rms(SETTLED)

        assertTrue(
            "HIGH must be clearly beyond MEDIUM, which is the old +12dB ceiling: $medium vs $high",
            high > medium * MIN_STEP,
        )
    }

    @Test
    fun `each level is louder than the one below it`() {
        val quiet = tone(QUIET)
        val levels = listOf(VolumeBoost.LOW, VolumeBoost.MEDIUM, VolumeBoost.HIGH, VolumeBoost.MAX)

        val loudness = levels.map { boosted(quiet, it).rms(SETTLED) }
        loudness.zipWithNext().forEach { (lower, higher) ->
            assertTrue("levels must increase monotonically: $loudness", higher > lower)
        }
    }

    // ---- and it must not sound broken ----------------------------------------------------------

    /**
     * The reason a flat gain could not be turned up: it clips.
     *
     * A loud recording at +30 dB of naive gain is a square wave. Here the limiter has to hold it
     * inside full scale, which is what buys the headroom for the levels to be this high at all.
     */
    @Test
    fun `already-loud audio is not clipped into distortion`() {
        val alreadyLoud = tone(LOUD)
        val output = boosted(alreadyLoud, VolumeBoost.MAX)

        // Against the magnitude of Short.MIN_VALUE, not MAX_VALUE: -32768 is a perfectly legal
        // sample whose absolute value is 32768, so comparing to 32767 fails on correct output.
        assertTrue("nothing may exceed full scale", output.peak() <= -Short.MIN_VALUE.toInt())
        // A clipped sine spends much of its time pinned at the rail; a limited one does not.
        val pinned = output.count { abs(it.toInt()) >= Short.MAX_VALUE - 1 }
        assertTrue(
            "output is pinned to the rail for $pinned of ${output.size} samples — that is clipping",
            pinned < output.size / PINNED_FRACTION,
        )
    }

    /** Wrapping is worse than clipping: it inverts the waveform and is heard as a crack. */
    @Test
    fun `samples never wrap around`() {
        val output = boosted(tone(LOUD), VolumeBoost.MAX)
        val input = tone(LOUD)

        // A wrap turns a large positive sample into a large negative one, so signs must agree
        // wherever the input is well away from zero.
        var inversions = 0
        input.indices.forEach { i ->
            if (abs(input[i].toInt()) > WELL_AWAY && input[i] > 0 != output[i] > 0) inversions++
        }
        assertEquals("a wrapped sample inverts the waveform and is heard as a crack", 0, inversions)
    }

    /**
     * Hiss must not come up with the speech.
     *
     * +30 dB applied to room noise between sentences is unbearable, and it is the single most likely
     * way a strong boost becomes unusable rather than merely loud.
     */
    @Test
    fun `room noise is not amplified into a roar`() {
        val hiss = tone(HISS)
        val output = boosted(hiss, VolumeBoost.MAX)

        val before = hiss.rms(SETTLED)
        val after = output.rms(SETTLED)
        assertTrue(
            "noise at the floor must stay near where it was: ${before.toInt()} -> ${after.toInt()}",
            after < before * MAX_NOISE_LIFT,
        )
    }

    @Test
    fun `digital silence stays silent`() {
        val output = boosted(ShortArray(rate), VolumeBoost.MAX)

        assertEquals(0, output.peak())
    }

    /**
     * The gain glides rather than switching.
     *
     * Recomputing the gain per sample and applying it immediately modulates the waveform at audio
     * rate, which is heard as distortion rather than as level control. A steady tone must come out
     * steady, so consecutive samples cannot jump wildly relative to the signal.
     */
    @Test
    fun `a steady tone comes out steady rather than modulated`() {
        val output = boosted(tone(QUIET), VolumeBoost.HIGH)

        // Compare the second half's loudness against the third quarter's: once settled, a steady
        // input must produce a steady output.
        val thirdQuarter = output.copyOfRange(output.size / 2, output.size * 3 / 4).rms()
        val fourthQuarter = output.copyOfRange(output.size * 3 / 4, output.size).rms()
        val ratio = fourthQuarter / thirdQuarter
        assertTrue("a steady tone wobbled by ${(ratio - 1) * 100}%", abs(ratio - 1) < STEADY_TOLERANCE)
    }

    // ---- off means off --------------------------------------------------------------------------

    /** Bit-exact, not merely quiet: OFF must not round-trip every sample of every stream forever. */
    @Test
    fun `off leaves the audio bit-for-bit identical`() {
        val input = tone(MID)

        assertArrayEquals(input, boosted(input, VolumeBoost.OFF))
    }

    @Test
    fun `the smoothing rate follows the sample rate`() {
        // Same time constant, half the sample rate: the per-sample coefficient must be larger, or a
        // boost tuned at 44.1kHz would attack twice as slowly at 22.05.
        val fast = LoudnessBoost.coefficientFor(milliseconds = 5f, sampleRate = 22_050)
        val slow = LoudnessBoost.coefficientFor(milliseconds = 5f, sampleRate = 44_100)

        assertTrue("$fast should exceed $slow", fast > slow)
    }

    @Test
    fun `a nonsense sample rate does not divide by zero`() {
        assertEquals(1f, LoudnessBoost.coefficientFor(milliseconds = 5f, sampleRate = 0))
    }

    private companion object {
        const val FULL_SCALE = 32_767f

        /** About -40 dBFS: a podcast mastered far too quietly, which is the case this is all for. */
        const val QUIET = 0.01f

        /** About -3 dBFS: an already-loud recording, the one a naive gain destroys. */
        const val LOUD = 0.7f
        const val MID = 0.2f

        /** Below the noise floor — tape hiss, a room, the gap between sentences. */
        const val HISS = 0.002f

        /** Samples to skip before measuring, so the gain has finished gliding up. */
        const val SETTLED = 22_050

        /** MAX is +30 dB, a factor of ~31 in amplitude; well over 10x proves it is doing the work. */
        const val MIN_MAX_LIFT = 10.0

        /** HIGH is +20 dB against MEDIUM's +12, so at least a 2x step. */
        const val MIN_STEP = 2.0

        /** A clipped sine is pinned for a large share of its period; a limited one is not. */
        const val PINNED_FRACTION = 20

        /** Noise may come up a little as the taper is proportional, but nothing like the speech. */
        const val MAX_NOISE_LIFT = 3.0

        /** A steady tone may wobble a few percent from the envelope follower, not more. */
        const val STEADY_TOLERANCE = 0.05

        /** Far enough from zero that a sign change means a wrap rather than a rounded crossing. */
        const val WELL_AWAY = 1_000
    }
}
