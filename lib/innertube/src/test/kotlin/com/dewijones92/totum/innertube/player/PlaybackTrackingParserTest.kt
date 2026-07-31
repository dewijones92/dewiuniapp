package com.dewijones92.totum.innertube.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading the tracking URLs out of a `/player` response.
 *
 * The shapes here are trimmed from real responses captured on 2026-07-31 while proving why
 * watch history never reached the account.
 */
class PlaybackTrackingParserTest {

    @Test
    fun `reads both URLs`() {
        val tracking = PlaybackTrackingParser.parse(
            """
            {"playabilityStatus":{"status":"OK"},"playbackTracking":{
              "videostatsPlaybackUrl":{"baseUrl":"https://s.youtube.com/api/stats/playback?docid=a&uga=m34"},
              "videostatsWatchtimeUrl":{"baseUrl":"https://s.youtube.com/api/stats/watchtime?docid=a&uga=m34"}
            }}
            """.trimIndent(),
        )

        assertEquals("https://s.youtube.com/api/stats/playback?docid=a&uga=m34", tracking?.playbackUrl)
        assertEquals("https://s.youtube.com/api/stats/watchtime?docid=a&uga=m34", tracking?.watchtimeUrl)
    }

    @Test
    fun `a missing playback URL still gives a usable session — watchtime is the one that matters`() {
        val tracking = PlaybackTrackingParser.parse(
            """{"playbackTracking":{"videostatsWatchtimeUrl":{"baseUrl":"https://s.youtube.com/w?docid=a"}}}""",
        )

        assertNull(tracking?.playbackUrl)
        assertEquals("https://s.youtube.com/w?docid=a", tracking?.watchtimeUrl)
    }

    @Test
    fun `no watchtime URL is no tracking at all, since there is nothing to report to`() {
        assertNull(
            PlaybackTrackingParser.parse(
                """{"playbackTracking":{"videostatsPlaybackUrl":{"baseUrl":"https://s.youtube.com/p"}}}""",
            ),
        )
    }

    @Test
    fun `a refused response carries no tracking, and is a null rather than a throw`() {
        // What YouTube answers when the signature timestamp is missing or stale.
        val refusal = """{"playabilityStatus":{"status":"UNPLAYABLE","reason":"The page needs to be reloaded."}}"""

        assertNull(PlaybackTrackingParser.parse(refusal))
        assertNull(PlaybackTrackingParser.parse("not json"))
    }
}
