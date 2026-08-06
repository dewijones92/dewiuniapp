package com.dewijones92.totum.support

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A silent WAV, generated rather than shipped.
 *
 * The repository carries no audio, and a generated file is also **copyright-free by construction**
 * — which matters for the torrent tests, where the fixtures name public-domain films and no real
 * media may be involved.
 *
 * Silent because these tests assert positions and sources, never sound. One caveat worth knowing:
 * with skip-silence on, sample removal deletes the entire file and playback never starts, which
 * reads as "the item never played". Every test using this turns skip-silence off explicitly.
 */
object SilentWav {

    fun bytes(seconds: Int): ByteArray {
        val samples = SAMPLE_RATE * seconds
        val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(WAV_HEADER_BYTES - RIFF_PREAMBLE + samples)
        header.put("WAVEfmt ".toByteArray())
        header.putInt(FMT_CHUNK_BYTES)
        header.putShort(PCM_FORMAT)
        header.putShort(MONO)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE)
        header.putShort(BLOCK_ALIGN)
        header.putShort(BITS_PER_SAMPLE)
        header.put("data".toByteArray())
        header.putInt(samples)
        val out = ByteArrayOutputStream()
        out.write(header.array())
        out.write(ByteArray(samples) { SILENCE })
        return out.toByteArray()
    }

    private const val SAMPLE_RATE = 8_000
    private const val WAV_HEADER_BYTES = 44
    private const val RIFF_PREAMBLE = 8
    private const val FMT_CHUNK_BYTES = 16
    private const val PCM_FORMAT: Short = 1
    private const val MONO: Short = 1
    private const val BLOCK_ALIGN: Short = 1
    private const val BITS_PER_SAMPLE: Short = 8
    private const val SILENCE: Byte = -128
}
