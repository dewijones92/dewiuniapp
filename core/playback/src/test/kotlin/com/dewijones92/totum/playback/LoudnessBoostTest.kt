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
 * Whether a quiet podcast becomes audible — without the distortion Dewi reported by ear.
 *
 * Dewi, 2026-08-08, on the fixed-level version: *"the volume booster setting thing in the app causes
 * distortion … i dont want distortion, i want it to allow me to hear things like quiet podcasts
 * well"*, at MEDIUM and above, on earphones.
 *
 * **The tests that shipped that bug were all wrong in the same way: every one of them used a
 * CONSTANT tone.** The gain had always finished settling before anything was measured, so a defect
 * that only exists during a *change* of level was invisible to all eleven of them. The clipping test
 * even allowed 5% of samples pinned to the rail, and the real clipping came to 3% — it passed by
 * squeaking under a bar that should never have been above zero. Real speech is quiet and then
 * somebody laughs, so the tests below lead with that.
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
        for (i in from until size) sum += this[i].toDouble() * this[i].toDouble()
        return sqrt(sum / (size - from))
    }

    private fun ShortArray.peak(): Int {
        var best = 0
        forEach { best = kotlin.math.max(best, abs(it.toInt())) }
        return best
    }

    private fun ShortArray.pinned(): Int = count { abs(it.toInt()) >= Short.MAX_VALUE - 1 }

    private fun boost(level: VolumeBoost = VolumeBoost.AUTO): LoudnessBoost =
        LoudnessBoost(rate).apply { this.level = level }

    private fun boosted(input: ShortArray, level: VolumeBoost = VolumeBoost.AUTO): ShortArray {
        val copy = input.copyOf()
        boost(level).process(copy, copy.size)
        return copy
    }

    // ---- it must not distort --------------------------------------------------------------------

    /**
     * THE DEFECT Dewi heard. Two seconds of quiet, then full-level audio arriving in one sample.
     *
     * The old version was still applying the full makeup gain when the loud part landed and took tens
     * of milliseconds to wind down, slicing every sample flat in between — **5428 of them (123 ms) at
     * MAX and 2844 (64 ms) at MEDIUM**, on every transient. A hard cap is not protection from
     * distortion, it IS the distortion.
     *
     * Now the gain falls instantly and only recovers slowly, and the fall is clamped per sample to
     * `CEILING / |sample|`, so the output is bounded by construction. Not "rarely clips" — cannot.
     */
    @Test
    fun `a sudden loud passage after a quiet one is not clipped`() {
        val output = boosted(tone(QUIET, seconds = 2f) + tone(LOUD, seconds = 1f))

        assertEquals("samples sliced flat at the ceiling — that is the crunch", 0, output.pinned())
    }

    /** The processor's own count of samples that hit the rail, which is what a report will carry. */
    @Test
    fun `nothing is reported as clipped`() {
        val input = tone(QUIET, seconds = 2f) + tone(LOUD, seconds = 1f)
        val boost = boost()

        boost.process(input.copyOf().also { boost.process(it, it.size) }, input.size)

        assertEquals("clipped=0 is the claim this design makes", 0L, boost.clippedSamples)
    }

    /** Repeated transients, since a conversation is nothing but changes of level. */
    @Test
    fun `alternating quiet and loud passages never clip`() {
        var input = ShortArray(0)
        repeat(BURSTS) { input += tone(QUIET, seconds = 0.5f) + tone(LOUD, seconds = 0.5f) }

        assertEquals(0, boosted(input).pinned())
    }

    /** Wrapping is worse than clipping: it inverts the waveform and is heard as a crack. */
    @Test
    fun `samples never wrap around`() {
        val input = tone(QUIET, seconds = 2f) + tone(LOUD, seconds = 1f)
        val output = boosted(input)

        var inversions = 0
        input.indices.forEach { i ->
            if (abs(input[i].toInt()) > WELL_AWAY && input[i] > 0 != output[i] > 0) inversions++
        }
        assertEquals("a wrapped sample inverts the waveform and is heard as a crack", 0, inversions)
    }

    // ---- it must make quiet things audible -------------------------------------------------------

    /** THE POINT. Speech recorded far too quietly has to become properly audible. */
    @Test
    fun `a very quiet recording is lifted a long way`() {
        val quiet = tone(QUIET, seconds = 3f)

        val loud = boosted(quiet)

        val before = quiet.rms(SETTLED)
        val after = loud.rms(SETTLED)
        assertTrue(
            "expected a large lift; went from ${before.toInt()} to ${after.toInt()}",
            after > before * MIN_LIFT,
        )
    }

    /**
     * ...and an already-loud one is left alone, which is the half a fixed level could never do.
     *
     * This is what "make everything the same loudness" means, and why it cannot over-drive: the gain
     * is measured from the audio rather than chosen by ear, so a recording that does not need help
     * does not get any. Asking for gain that was not needed is precisely what sounded squashed.
     */
    @Test
    fun `a recording that is already loud enough is barely touched`() {
        val alreadyLoud = tone(LOUD, seconds = 3f)

        val output = boosted(alreadyLoud)

        val ratio = output.rms(SETTLED) / alreadyLoud.rms(SETTLED)
        assertTrue("a loud recording was changed by ${(ratio - 1) * 100}%", abs(ratio - 1) < LEFT_ALONE)
    }

    /** Quieter in, more gain — the relationship the whole idea rests on. */
    @Test
    fun `the quieter the recording, the more gain it gets`() {
        val levels = listOf(0.005f, 0.02f, 0.08f, 0.3f)

        val gains = levels.map { amplitude ->
            val input = tone(amplitude, seconds = 3f)
            boosted(input).rms(SETTLED) / input.rms(SETTLED)
        }

        gains.zipWithNext().forEach { (quieter, louder) ->
            assertTrue("gain must fall as the recording gets louder: $gains", louder <= quieter)
        }
        assertTrue("the quietest must actually be boosted: $gains", gains.first() > MIN_LIFT)
    }

    /** It never makes anything quieter — the ask was to HEAR things. */
    @Test
    fun `nothing is ever turned down`() {
        val veryLoud = tone(0.9f, seconds = 3f)

        val output = boosted(veryLoud)

        assertTrue(
            "a loud recording must not be attenuated: ${veryLoud.rms(SETTLED)} -> ${output.rms(SETTLED)}",
            output.rms(SETTLED) >= veryLoud.rms(SETTLED) * NOT_QUIETER,
        )
    }

    /** And never more than +20dB — Dewi's trade of top-end loudness for audio that sounds natural. */
    @Test
    fun `the gain is capped so nothing ends up crushed`() {
        val nearlySilent = tone(0.001f, seconds = 3f)

        val output = boosted(nearlySilent)

        val gain = output.rms(SETTLED) / nearlySilent.rms(SETTLED)
        assertTrue("gain reached ${LoudnessBoost.decibels(gain.toFloat())}dB, past the +20 cap", gain <= MAX_GAIN)
    }

    /**
     * It reaches a sensible gain quickly.
     *
     * Waiting for a slow average would leave every episode starting unboosted and audibly swelling
     * over the first couple of seconds — the app changing the volume on its own, which is exactly
     * what Dewi has ruled out elsewhere.
     */
    @Test
    fun `it settles within the first moments rather than swelling`() {
        val output = boosted(tone(QUIET, seconds = 3f))

        val early = output.copyOfRange(rate / 2, rate).rms()
        val settled = output.copyOfRange(output.size - rate / 2, output.size).rms()
        assertTrue(
            "still climbing after half a second: $early vs $settled",
            early > settled * MOSTLY_THERE,
        )
    }

    // ---- and it must not sound processed ---------------------------------------------------------

    /**
     * A steady tone comes out steady.
     *
     * Recomputing the gain per sample and applying it immediately modulates the waveform at audio
     * rate, which is heard as distortion rather than as level control. The per-sample ceiling clamp
     * is allowed to pull the gain down instantly, but it must not then chase the waveform back up.
     */
    @Test
    fun `a steady tone comes out steady rather than modulated`() {
        val output = boosted(tone(QUIET, seconds = 3f))

        val thirdQuarter = output.copyOfRange(output.size / 2, output.size * 3 / 4).rms()
        val fourthQuarter = output.copyOfRange(output.size * 3 / 4, output.size).rms()
        val ratio = fourthQuarter / thirdQuarter
        assertTrue("a steady tone wobbled by ${(ratio - 1) * 100}%", abs(ratio - 1) < STEADY_TOLERANCE)
    }

    /**
     * A pause between sentences must not wind the gain up, or every gap would end in a blast.
     *
     * This replaced a test asserting that a stretch of pure hiss is never boosted, which was the
     * wrong claim twice over. It is not what happens in a podcast — hiss appears *between speech*,
     * not on its own — and taken literally it forced an absolute noise floor, which is what made
     * genuinely quiet speech invisible to the level estimate and left it unboosted. What actually
     * matters is that the gain does not move across a gap, and that is a relative judgement: the
     * hiss is quiet *compared to the speech either side of it*.
     */
    @Test
    fun `a pause between sentences does not change the gain`() {
        val speech = tone(SOFT, seconds = 2f)
        val withPause = speech + tone(HISS, seconds = 1f) + tone(SOFT, seconds = 2f)

        val steady = boosted(speech + tone(SOFT, seconds = 3f))
        val paused = boosted(withPause)

        // Compare the final stretch of speech in each: the pause must have left the gain where it was.
        val expected = steady.copyOfRange(steady.size - rate, steady.size).rms()
        val actual = paused.copyOfRange(paused.size - rate, paused.size).rms()
        assertTrue(
            "the gap moved the gain: $expected vs $actual",
            abs(actual / expected - 1) < LEFT_ALONE,
        )
    }

    /** And the hiss inside that gap comes up no more than the speech does — it is one gain, not two. */
    @Test
    fun `the gap is not amplified more than the speech around it`() {
        val input = tone(SOFT, seconds = 2f) + tone(HISS, seconds = 1f) + tone(SOFT, seconds = 2f)

        val output = boosted(input)

        val gapIn = input.copyOfRange(rate * 2, rate * 3).rms()
        val gapOut = output.copyOfRange(rate * 2, rate * 3).rms()
        val speechIn = input.copyOfRange(input.size - rate, input.size).rms()
        val speechOut = output.copyOfRange(output.size - rate, output.size).rms()
        assertTrue(
            "hiss lifted ${gapOut / gapIn}x against speech's ${speechOut / speechIn}x",
            gapOut / gapIn <= speechOut / speechIn + LEFT_ALONE,
        )
    }

    /**
     * Quiet speech is lifted, not chopped.
     *
     * The previous version tapered the gain away below the noise floor, which is a downward expander
     * and eats the quiet ends of words. That taper existed only to stop a fixed +30 dB turning hiss
     * into a roar, and automatic gain removes the need for it — so quiet audio above the floor must
     * come through whole, at a steady gain.
     */
    @Test
    fun `quiet speech is lifted evenly rather than gated`() {
        val output = boosted(tone(QUIET, seconds = 3f))

        // A gate shows up as a collapsed level near the zero crossings, which drags the average down
        // relative to the peak. A clean gain keeps the sine's own ratio of ~0.707.
        val crestFactor = output.peak() / output.rms(SETTLED)
        assertTrue("the waveform is being gated, not amplified: crest factor $crestFactor", crestFactor < GATED_ABOVE)
    }

    @Test
    fun `digital silence stays silent`() {
        assertEquals(0, boosted(ShortArray(rate)).peak())
    }

    // ---- off means off ---------------------------------------------------------------------------

    /** Bit-exact, not merely quiet: OFF must not round-trip every sample of every stream forever. */
    @Test
    fun `off leaves the audio bit-for-bit identical`() {
        val input = tone(MID)

        assertArrayEquals(input, boosted(input, VolumeBoost.OFF))
    }

    /** A level saved before the switch to automatic must keep the boost ON, not silently disable it. */
    @Test
    fun `an old stored level becomes auto rather than off`() {
        listOf("LOW", "MEDIUM", "HIGH", "MAX").forEach { old ->
            assertEquals("$old should survive the upgrade as AUTO", VolumeBoost.AUTO, VolumeBoost.fromStoredName(old))
        }
        assertEquals(VolumeBoost.OFF, VolumeBoost.fromStoredName("OFF"))
        assertEquals(VolumeBoost.OFF, VolumeBoost.fromStoredName(null))
        assertEquals(VolumeBoost.AUTO, VolumeBoost.fromStoredName("AUTO"))
    }

    // ---- arithmetic --------------------------------------------------------------------------------

    @Test
    fun `the smoothing rate follows the sample rate`() {
        // Same time constant, half the sample rate: the per-sample coefficient must be larger, or a
        // boost tuned at 44.1kHz would settle twice as slowly at 22.05.
        val fast = LoudnessBoost.coefficientFor(milliseconds = 5f, sampleRate = 22_050)
        val slow = LoudnessBoost.coefficientFor(milliseconds = 5f, sampleRate = 44_100)

        assertTrue("$fast should exceed $slow", fast > slow)
    }

    @Test
    fun `a nonsense sample rate does not divide by zero`() {
        assertEquals(1f, LoudnessBoost.coefficientFor(milliseconds = 5f, sampleRate = 0))
    }

    @Test
    fun `gain converts to decibels the way the ear reads it`() {
        assertEquals(0f, LoudnessBoost.decibels(1f), DB_TOLERANCE)
        assertEquals(20f, LoudnessBoost.decibels(10f), DB_TOLERANCE)
        assertEquals(6f, LoudnessBoost.decibels(2f), DB_TOLERANCE_COARSE)
    }

    private companion object {
        const val FULL_SCALE = 32_767f

        /** About -40 dBFS: a podcast mastered far too quietly, which is the case this is all for. */
        const val QUIET = 0.01f

        /** About -3 dBFS: an already-loud recording, the one a naive gain destroys. */
        const val LOUD = 0.7f
        const val MID = 0.2f

        /** Tape hiss, a room, the gap between sentences — quiet RELATIVE to the speech around it. */
        const val HISS = 0.002f

        /** Softly recorded speech: quiet enough to need help, loud enough not to hit the +20dB cap. */
        const val SOFT = 0.05f

        /** Samples to skip before measuring, so the gain has settled. */
        const val SETTLED = 88_200

        /** The cap is 10x, so a quiet recording should be getting most of it. */
        const val MIN_LIFT = 8.0
        const val MAX_GAIN = 10.5

        /** A recording that needs no help should come through within a few percent of untouched. */
        const val LEFT_ALONE = 0.05

        /** Never attenuated — allowing only for rounding in the 16-bit round trip. */
        const val NOT_QUIETER = 0.999

        /** Half a second in, it should already be most of the way to its final gain. */
        const val MOSTLY_THERE = 0.8

        /** A steady tone may wobble a few percent from the level estimate, not more. */
        const val STEADY_TOLERANCE = 0.05

        /** A clean sine's peak-to-RMS is ~1.41; a gated one is far peakier. */
        const val GATED_ABOVE = 1.6

        /** Far enough from zero that a sign change means a wrap rather than a rounded crossing. */
        const val WELL_AWAY = 1_000
        const val BURSTS = 6
        const val DB_TOLERANCE = 0.01f
        const val DB_TOLERANCE_COARSE = 0.1f
    }
}
