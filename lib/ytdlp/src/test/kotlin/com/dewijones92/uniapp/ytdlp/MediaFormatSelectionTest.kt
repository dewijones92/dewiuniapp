package com.dewijones92.uniapp.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaFormatSelectionTest {

    private fun metadata(vararg formats: MediaFormat) = MediaMetadata(
        id = "id",
        title = "t",
        uploader = null,
        durationSeconds = null,
        thumbnailUrl = null,
        formats = formats.toList(),
    )

    private fun format(
        id: String,
        hasVideo: Boolean = true,
        hasAudio: Boolean = true,
        height: Int? = if (hasVideo) 360 else null,
        url: String? = "https://cdn.example.com/$id",
        sizeBytes: Long? = null,
    ) = MediaFormat(
        formatId = id,
        container = "mp4",
        width = null,
        height = height,
        hasVideo = hasVideo,
        hasAudio = hasAudio,
        fileSizeBytes = sizeBytes,
        url = url,
    )

    @Test
    fun `prefers highest-resolution premuxed format`() {
        val best = metadata(
            format("v-only", hasAudio = false, height = 2160),
            format("premuxed-360", height = 360),
            format("premuxed-720", height = 720),
            format("audio", hasVideo = false),
        ).bestPlayableFormat()
        assertEquals("premuxed-720", best?.formatId)
    }

    @Test
    fun `falls back to biggest audio-only stream when nothing is premuxed`() {
        val best = metadata(
            format("v-only", hasAudio = false, height = 1080),
            format("audio-small", hasVideo = false, sizeBytes = 100),
            format("audio-big", hasVideo = false, sizeBytes = 900),
        ).bestPlayableFormat()
        assertEquals("audio-big", best?.formatId)
    }

    @Test
    fun `ignores formats without a stream url`() {
        val best = metadata(format("premuxed", url = null)).bestPlayableFormat()
        assertNull(best)
    }

    @Test
    fun `a format must carry audio or video`() {
        assertThrows(IllegalArgumentException::class.java) {
            format("storyboard", hasVideo = false, hasAudio = false, height = null)
        }
    }
}
