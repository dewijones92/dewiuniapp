package com.dewijones92.totum.playback

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.dewijones92.totum.common.Diag
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Puts [LoudnessBoost] in the sink's processing chain, so the boost is part of playback itself.
 *
 * In the chain rather than on the audio session, which is where the old platform `LoudnessEnhancer`
 * sat. Three things follow from that and all of them matter:
 *
 * - **No platform ceiling.** `LoudnessEnhancer` is a device implementation with its own cap; this is
 *   arithmetic on the samples, so +30 dB means +30 dB on every phone.
 * - **It can compress.** A session effect gets one flat gain knob. Being in the stream means seeing
 *   the waveform, which is the only way to lift a quiet passage without clipping a loud one.
 * - **One place, both pillars, and it survives a session change.** The enhancer was bound to an audio
 *   session id and had to be torn down and rebuilt whenever that changed — a stale one silently did
 *   nothing, which is a bug shape this removes rather than fixes.
 *
 * Only 16-bit PCM is touched. Anything else passes through untouched rather than being reinterpreted
 * as samples, exactly as [SilenceDetectingAudioProcessor] does beside it in the same chain.
 */
@OptIn(markerClass = [UnstableApi::class])
@UnstableApi
internal class BoostingAudioProcessor : BaseAudioProcessor() {

    private var boost: LoudnessBoost? = null
    private var samples = ShortArray(0)

    /** Set from the session command; takes effect on the next buffer, glided rather than switched. */
    var level: VolumeBoost = VolumeBoost.OFF
        set(value) {
            field = value
            boost?.level = value
            Diag.log("boost", "level -> $value (${value.decibels}dB makeup)")
        }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Rebuilt per configuration because the smoothing coefficients depend on the sample rate —
        // a boost tuned at 44.1kHz would attack twice as slowly at 22.05.
        boost = if (inputAudioFormat.encoding == ENCODING_16BIT) {
            LoudnessBoost(inputAudioFormat.sampleRate).apply { level = this@BoostingAudioProcessor.level }
        } else {
            // Said out loud: silently passing audio through is indistinguishable from a boost that
            // does nothing, and "the booster stopped working" would have no other explanation.
            Diag.warn("boost", "encoding ${inputAudioFormat.encoding} is not 16-bit PCM; not boosting")
            null
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val active = boost
        if (active == null || level == VolumeBoost.OFF) {
            // Straight through, byte for byte.
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }

        val count = remaining / BYTES_PER_SAMPLE
        if (samples.size < count) samples = ShortArray(count)
        // Little-endian: the order the sink hands 16-bit PCM over in. Reading it the other way round
        // would turn every sample into noise, which is the kind of mistake that is obvious on a
        // device and invisible in a type checker.
        val shorts = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        shorts.get(samples, 0, count)
        active.process(samples, count)

        val output = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        output.asShortBuffer().put(samples, 0, count)
        output.position(remaining)
        output.flip()
        // The input has been consumed; the sink checks this rather than taking it on trust.
        inputBuffer.position(inputBuffer.limit())
    }

    override fun onReset() {
        boost = null
        samples = ShortArray(0)
    }

    private companion object {
        /** `C.ENCODING_PCM_16BIT`, named locally so this file needs no Media3 constant import. */
        const val ENCODING_16BIT = 2
        const val BYTES_PER_SAMPLE = 2
    }
}
