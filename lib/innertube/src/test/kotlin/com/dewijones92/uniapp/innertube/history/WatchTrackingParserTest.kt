package com.dewijones92.uniapp.innertube.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchTrackingParserTest {

    @Test
    fun `pulls both stats base urls out of playbackTracking`() {
        val body = """
            {"playbackTracking": {
                "videostatsPlaybackUrl": {"baseUrl": "https://s.youtube.com/api/stats/playback?docid=abc&ei=E"},
                "videostatsWatchtimeUrl": {"baseUrl": "https://s.youtube.com/api/stats/watchtime?docid=abc&ei=E"}
            }}
        """.trimIndent()

        val tracking = WatchTrackingParser.parse(body)

        assertEquals("https://s.youtube.com/api/stats/playback?docid=abc&ei=E", tracking?.playbackUrl)
        assertEquals("https://s.youtube.com/api/stats/watchtime?docid=abc&ei=E", tracking?.watchtimeUrl)
    }

    @Test
    fun `missing tracking is a null, not a crash`() {
        assertNull(WatchTrackingParser.parse("""{"videoDetails":{"videoId":"abc"}}"""))
        assertNull(WatchTrackingParser.parse("not json"))
    }
}
