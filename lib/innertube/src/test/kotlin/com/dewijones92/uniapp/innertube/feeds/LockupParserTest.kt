package com.dewijones92.uniapp.innertube.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses real captured channel-tab responses (WEB browse, 2026-07-24). */
class LockupParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "fixture $name missing" }
            .bufferedReader().readText()

    @Test
    fun `channel Videos tab parses with published dates`() {
        val videos = LockupParser.videos(fixture("channel_videos_web_sample.json"))
        assertTrue("expected videos", videos.size > 10)
        val first = videos.first()
        assertEquals("8Hx2yvWSgs0", first.videoId)
        assertTrue("title present", first.title.isNotBlank())
        assertEquals("https://www.youtube.com/watch?v=8Hx2yvWSgs0", first.watchUrl.value)
        // The date — the whole point. Most uploads carry "x ago" text.
        assertTrue("published dates present", videos.count { it.publishedText != null } > videos.size / 2)
        assertTrue("a duration parsed", videos.any { it.durationSeconds != null })
    }

    @Test
    fun `channel Shorts tab parses and tags SHORT`() {
        val shorts = LockupParser.shorts(fixture("channel_shorts_web_sample.json"))
        assertTrue("expected shorts", shorts.size > 5)
        assertTrue("all SHORT", shorts.all { it.kind == FeedVideo.Kind.SHORT })
        assertTrue("ids present", shorts.all { it.videoId.isNotBlank() })
        assertTrue("titles present", shorts.all { it.title.isNotBlank() })
        assertEquals("https://www.youtube.com/watch?v=${shorts.first().videoId}", shorts.first().watchUrl.value)
    }

    @Test
    fun `channel Playlists tab parses to VL browse ids`() {
        val playlists = LockupParser.playlists(fixture("channel_playlists_web_sample.json"))
        assertTrue("expected playlists", playlists.size > 5)
        assertTrue("VL-prefixed browse ids", playlists.all { it.browseId.startsWith("VL") })
        assertTrue("titles present", playlists.all { it.title.isNotBlank() })
    }
}
