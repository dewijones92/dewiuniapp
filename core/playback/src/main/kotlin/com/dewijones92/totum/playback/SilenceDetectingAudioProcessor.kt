package com.dewijones92.totum.playback

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.dewijones92.totum.common.Diag
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Passes audio through untouched and reports when it goes quiet, so silence can be
 * handled by **changing the playback rate** rather than by removing samples.
 *
 * That distinction is the whole point. Media3's `SilenceSkippingAudioProcessor`
 * shortens the audio stream but not the video clock, so on a video the audio runs
 * ahead of the picture (measured: ~6s over a 20s clip) — which is why skip-silence
 * used to be audio-only. Speeding up retimes audio *and* video together, so it cannot
 * desync, and there is no seeking, so no keyframe stutter either.
 *
 * **Why acting immediately is accurate enough:** this sits in the sink's chain, and the
 * sink consumes buffers in real time — the audio track holds only a few hundred
 * milliseconds. So "just saw silence here" means "about to be heard", and the constant
 * lead is small relative to a gap worth skipping. Short gaps simply get a brief nudge.
 */
@OptIn(markerClass = [UnstableApi::class])
@UnstableApi
internal class SilenceDetectingAudioProcessor(
    private val onSilenceChanged: (Boolean) -> Unit,
) : BaseAudioProcessor() {

    private var silentBuffers = 0
    private var reportedSilent = false
    private var lastPeak = 0
    private var silenceCount = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Diag.log(
            "silence",
            "configured enc=${inputAudioFormat.encoding} " +
                "rate=${inputAudioFormat.sampleRate} ch=${inputAudioFormat.channelCount}",
        )
        // 16-bit PCM is what the sink hands us after decoding; anything else passes
        // through unexamined rather than being misread as silence.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val quiet = inputBuffer.isQuiet()

        // Entering silence needs a few consecutive quiet buffers so a momentary dip
        // between words doesn't trigger; leaving is immediate, so speech is never
        // clipped by the speed-up lingering.
        if (quiet) {
            silentBuffers++
            if (!reportedSilent && silentBuffers >= BUFFERS_TO_ENTER) {
                reportedSilent = true
                // Silence is entered every few seconds in speech, so logging each one
                // would flood the crash-report event trail and evict what matters.
                // The first tells you detection works at all; a periodic count keeps
                // proving it without drowning the trail.
                silenceCount++
                if (silenceCount == 1L || silenceCount % SILENCE_LOG_EVERY == 0L) {
                    Diag.log("silence", "entering silence #$silenceCount (peak=$lastPeak)")
                }
                onSilenceChanged(true)
            }
        } else {
            silentBuffers = 0
            if (reportedSilent) {
                reportedSilent = false
                onSilenceChanged(false)
            }
        }

        // Pass through unchanged: this processor only observes.
        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        // A seek invalidates what we thought we were hearing.
        silentBuffers = 0
        if (reportedSilent) {
            reportedSilent = false
            onSilenceChanged(false)
        }
    }

    /** True when every sampled frame sits below the silence threshold. */
    private fun ByteBuffer.isQuiet(): Boolean {
        val order = order()
        order(ByteOrder.LITTLE_ENDIAN)
        try {
            var index = position()
            val end = limit()
            // Sampling rather than reading every frame: a buffer is thousands of
            // samples and loudness doesn't hide between them.
            var peak = 0
            while (index + 1 < end) {
                val sample = abs(getShort(index).toInt())
                if (sample > peak) peak = sample
                index += STRIDE_BYTES
            }
            lastPeak = peak
            return peak <= SILENCE_THRESHOLD
        } finally {
            order(order)
        }
    }

    private companion object {
        /**
         * 16-bit amplitude below which audio counts as silence — the same value
         * Media3's own `SilenceSkippingAudioProcessor` defaults to. Measured on a real
         * clip, a stricter 128 never triggered: a recording's quiet passages still sit
         * well above the theoretical noise floor.
         */
        const val SILENCE_THRESHOLD = 1024

        /** How often a repeat silence entry is logged, so the event trail stays useful. */
        const val SILENCE_LOG_EVERY = 50L

        /**
         * Consecutive quiet buffers before we call it silence. A buffer here measured
         * ~25ms, so this is ~150ms — again matching Media3's own minimum, and short
         * enough that a real pause is caught while a gap between words isn't.
         */
        const val BUFFERS_TO_ENTER = 6

        /** Bytes between sampled frames (2 bytes per sample, so this is every 32nd frame). */
        const val STRIDE_BYTES = 64
    }
}
