package com.dewijones92.uniapp.ytdlp.chaquopy

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.DownloadEvent
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.VideoSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BridgeJsonTest {

    private val url = HttpUrl.of("https://example.com/watch?v=abc")

    @Test
    fun `parses versions`() {
        val versions = parseVersions("""{"yt_dlp":"2026.07.04","python":"3.13.2"}""")
        assertEquals("2026.07.04", versions.ytDlp)
        assertEquals("3.13.2", versions.python)
    }

    @Test
    fun `parses successful extraction with mixed formats`() {
        val text = """
            {"ok": true, "info": {
                "id": "abc", "title": "A video", "uploader": "A channel",
                "duration": 90.5, "thumbnail": "https://i.example.com/t.jpg",
                "formats": [
                    {"format_id": "22", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a",
                     "width": 1280, "height": 720, "filesize": 1000,
                     "url": "https://cdn.example.com/22.mp4"},
                    {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a",
                     "width": null, "height": null, "filesize_approx": 250},
                    {"format_id": "sb0", "ext": "mhtml", "vcodec": "none", "acodec": "none"},
                    {"ext": "mp4", "vcodec": "avc1"}
                ]
            }}
        """.trimIndent()

        val metadata = (parseExtraction(url, text) as ExtractionResult.Success).metadata

        assertEquals("abc", metadata.id)
        assertEquals(90L, metadata.durationSeconds)
        // Formats with no format_id or no codecs at all (storyboards) are dropped, not crashed on.
        assertEquals(2, metadata.formats.size)
        val (video, audio) = metadata.formats
        assertEquals(1280, video.width)
        assertEquals(false, video.isAudioOnly)
        assertEquals(1000L, video.fileSizeBytes)
        assertEquals("https://cdn.example.com/22.mp4", video.url)
        assertTrue(audio.isAudioOnly)
        assertNull(audio.width)
        assertNull(audio.url)
        assertEquals(250L, audio.fileSizeBytes)
    }

    @Test
    fun `parses search entries, dropping ones without id or url`() {
        val text = """
            {"ok": true, "entries": [
                {"id": "v1", "title": "First", "uploader": "Chan", "duration": 61.0,
                 "url": "https://www.youtube.com/watch?v=v1",
                 "thumbnail": "https://i.example.com/v1.jpg"},
                {"id": "v2", "url": "not a url"},
                {"title": "no id", "url": "https://example.com/x"}
            ]}
        """.trimIndent()

        val entries = (parseSearch(text) as VideoSearchResult.Success).entries

        assertEquals(1, entries.size)
        assertEquals("v1", entries[0].id)
        assertEquals("First", entries[0].title)
        assertEquals(61L, entries[0].durationSeconds)
        assertEquals("https://www.youtube.com/watch?v=v1", entries[0].watchUrl.value)
    }

    @Test
    fun `search failure maps to a value`() {
        val result = parseSearch("""{"ok": false, "kind": "network", "detail": "offline"}""")
        assertEquals(VideoSearchResult.Failure("offline"), result)
    }

    @Test
    fun `missing optional metadata degrades to defaults`() {
        val metadata = (parseExtraction(url, """{"ok": true, "info": {}}""") as ExtractionResult.Success).metadata
        assertEquals(url.value, metadata.id)
        assertEquals("Untitled", metadata.title)
        assertNull(metadata.durationSeconds)
        assertTrue(metadata.formats.isEmpty())
    }

    @Test
    fun `failure kinds map to sealed failures`() {
        assertEquals(
            ExtractionResult.Failure.UnsupportedUrl(url),
            parseExtraction(url, """{"ok": false, "kind": "unsupported", "detail": "x"}"""),
        )
        assertEquals(
            ExtractionResult.Failure.Network("timed out"),
            parseExtraction(url, """{"ok": false, "kind": "network", "detail": "timed out"}"""),
        )
        assertEquals(
            ExtractionResult.Failure.Extractor("geo"),
            parseExtraction(url, """{"ok": false, "kind": "extractor", "detail": "geo"}"""),
        )
    }

    @Test
    fun `download completion carries the output file`() {
        val event = parseDownloadCompletion(url, """{"ok": true, "filepath": "/sdcard/x.mp4"}""", ::File)
        assertEquals(File("/sdcard/x.mp4"), (event as DownloadEvent.Completed).file)
    }

    @Test
    fun `download failure and missing filepath map to Failed`() {
        assertTrue(
            parseDownloadCompletion(url, """{"ok": false, "kind": "network", "detail": "x"}""", ::File)
            is DownloadEvent.Failed,
        )
        assertTrue(parseDownloadCompletion(url, """{"ok": true}""", ::File) is DownloadEvent.Failed)
    }
}
