package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.ytdlp.MediaFormat
import com.dewijones92.uniapp.ytdlp.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quality ladder must only offer streams the device can decode. Selecting a
 * quality that no decoder can handle looked like "above 1080p doesn't play".
 */
class VideoQualityTest {

    private fun video(height: Int, codec: String, muxed: Boolean = false) = MediaFormat(
        formatId = "$codec-$height",
        container = "mp4",
        width = height * 16 / 9,
        height = height,
        hasVideo = true,
        hasAudio = muxed,
        fileSizeBytes = height.toLong(),
        url = "https://example.com/$codec-$height",
        videoCodec = codec,
        audioCodec = if (muxed) "mp4a.40.2" else null,
    )

    private val audio = MediaFormat(
        formatId = "audio",
        container = "m4a",
        width = null,
        height = null,
        hasVideo = false,
        hasAudio = true,
        fileSizeBytes = 1_000,
        url = "https://example.com/audio",
        videoCodec = null,
        audioCodec = "mp4a.40.2",
    )

    private fun metadata(vararg formats: MediaFormat) = MediaMetadata(
        id = "v",
        title = "v",
        uploader = null,
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = formats.toList(),
    )

    @Test
    fun `a height whose only codec is undecodable is not offered`() {
        // The reported bug: 2160p exists only as AV1, the device can't decode AV1,
        // and the app offered it anyway — so choosing it stopped playback.
        val meta = metadata(video(1080, "avc1.640028", muxed = true), video(2160, "av01.0.12M.08"), audio)
        val noAv1 = VideoCodecSupport { codec, _, _ -> codec?.startsWith("av01") != true }

        val heights = meta.videoQualities(noAv1).map { it.height }

        assertEquals(listOf(1080), heights)
    }

    @Test
    fun `a height with a decodable alternative codec is still offered`() {
        val meta = metadata(video(2160, "av01.0.12M.08"), video(2160, "vp09.00.50.08"), audio)
        val noAv1 = VideoCodecSupport { codec, _, _ -> codec?.startsWith("av01") != true }

        val qualities = meta.videoQualities(noAv1)

        assertEquals(listOf(2160), qualities.map { it.height })
        assertTrue("must use the decodable stream", qualities.single().videoUrl.value.contains("vp09"))
    }

    @Test
    fun `where several codecs decode, the more hardware-friendly one wins`() {
        val meta = metadata(video(1440, "av01.0.12M.08"), video(1440, "vp09.00.50.08"), audio)

        val quality = meta.videoQualities(VideoCodecSupport.Permissive).single()

        assertTrue("VP9 preferred over AV1", quality.videoUrl.value.contains("vp09"))
    }

    @Test
    fun `size matters, not just the codec`() {
        // Plenty of devices decode VP9 at 1080p but not at 2160p.
        val meta = metadata(video(1080, "vp09.00.50.08"), video(2160, "vp09.00.50.08"), audio)
        val upTo1080 = VideoCodecSupport { _, _, height -> (height ?: 0) <= 1080 }

        assertEquals(listOf(1080), meta.videoQualities(upTo1080).map { it.height })
    }

    @Test
    fun `a video-only height with no audio to merge is not offered`() {
        val meta = metadata(video(2160, "vp09.00.50.08"))

        assertTrue(meta.videoQualities(VideoCodecSupport.Permissive).isEmpty())
    }

    @Test
    fun `qualities are highest first`() {
        val meta = metadata(video(720, "avc1", muxed = true), video(1080, "avc1", muxed = true), audio)

        assertEquals(listOf(1080, 720), meta.videoQualities(VideoCodecSupport.Permissive).map { it.height })
    }
}
