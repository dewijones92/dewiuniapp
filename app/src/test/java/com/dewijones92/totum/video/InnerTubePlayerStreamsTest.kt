package com.dewijones92.totum.video

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.innertube.browse.InnerTubeClient
import com.dewijones92.totum.innertube.player.StreamingData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The anonymous player response is only offered once its URLs can actually be fetched.
 *
 * Every URL this client returns now carries an obfuscated `n` and 403s until it is solved —
 * measured 2026-08-02, 140 of 140 formats on one video. Handing those straight to the player
 * would turn the fast path from "starts in 0.2s" into "starts and then dies", which is strictly
 * worse than the 14-second extraction it replaces.
 */
class InnerTubePlayerStreamsTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun client() = InnerTubeClient(
        client = OkHttpClient(),
        playerUrl = server.url("/player").toString(),
    )

    /** A player response with one progressive format whose URL carries a raw `n`. */
    private fun respondWithFormat(n: String) = server.enqueue(
        MockResponse.Builder().code(200).body(
            """
            {"playabilityStatus":{"status":"OK"},
             "streamingData":{"formats":[
               {"itag":18,"mimeType":"video/mp4; codecs=\"avc1, mp4a\"","height":360,"bitrate":1000,
                "url":"https://x.test/videoplayback?itag=18&n=$n&sig=keep"}]},
             "videoDetails":{"videoId":"dQw4w9WgXcQ","title":"A video"}}
            """.trimIndent(),
        ).build(),
    )

    @Test
    fun `the anonymous response has its n solved before it is offered`() = runTest {
        respondWithFormat("RAW")
        val streams = InnerTubePlayerStreams(
            client(),
            solveN = { data -> data.replacingN("SOLVED") },
        )

        val url = streams.playerFor("dQw4w9WgXcQ")?.streaming?.directlyPlayable?.single()?.url?.value
        assertEquals("https://x.test/videoplayback?itag=18&n=SOLVED&sig=keep", url)
    }

    /**
     * Nothing fetchable is a NULL, so the caller extracts instead. Returning the response with
     * its raw `n` would look like success and fail at playback, which is the failure mode this
     * whole seam exists to avoid.
     */
    @Test
    fun `a response with nothing fetchable after solving is null`() = runTest {
        respondWithFormat("HOPELESS")
        val streams = InnerTubePlayerStreams(
            client(),
            // A solver that answers nothing — the real one drops formats it cannot solve.
            solveN = { data -> data.copy(formats = emptyList()) },
        )

        assertNull(streams.playerFor("dQw4w9WgXcQ"))
    }

    /** With no solver wired, the response passes through untouched — tests and previews. */
    @Test
    fun `no solver leaves the response alone`() = runTest {
        respondWithFormat("RAW")
        val streams = InnerTubePlayerStreams(client())

        val url = streams.playerFor("dQw4w9WgXcQ")?.streaming?.directlyPlayable?.single()?.url?.value
        assertEquals("https://x.test/videoplayback?itag=18&n=RAW&sig=keep", url)
    }

    /** A refusal is not a stream source, and must not be solved or offered as one. */
    @Test
    fun `a refused response yields no streams`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm your age"}}""",
            ).build(),
        )
        var solved = false
        val streams = InnerTubePlayerStreams(client(), solveN = { it.also { solved = true } })

        assertNull(streams.playerFor("dQw4w9WgXcQ"))
        assertEquals(false, solved)
    }

    private fun StreamingData.replacingN(value: String) = copy(
        formats = formats.map { format ->
            format.copy(url = format.url?.value?.replace(Regex("n=[^&]*"), "n=$value")?.let(HttpUrl::of))
        },
    )
}
