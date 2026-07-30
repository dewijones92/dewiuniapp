package com.dewijones92.totum.innertube.player

import com.dewijones92.totum.innertube.browse.InnerTubeClient
import com.dewijones92.totum.innertube.browse.InnerTubeResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hits YouTube for real. Off by default — CI must not depend on a third party's API — and
 * run with RUN_LIVE_YT=1 when changing anything about the player request, because the one
 * thing a fixture cannot tell you is whether YouTube still accepts the client we claim to be.
 */
class PlayerLiveTest {

    @Test
    fun `the android player request still works and still shows the SABR gap`() = runBlocking {
        assumeTrue("set RUN_LIVE_YT=1 to run", System.getenv("RUN_LIVE_YT") == "1")
        val client = InnerTubeClient(OkHttpClient())

        // A made-for-kids video: the case YouTube restricts to SABR today.
        val response = client.player("hTqtGJwsJVE")
        val body = (response as InnerTubeResponse.Success).body
        val streaming = (PlayerResponseParser.parse(body) as PlayerResult.Success).streaming

        println(
            "live: ${streaming.formats.size} formats, ${streaming.directlyPlayable.size} fetchable, " +
                "offered=${streaming.bestOfferedHeight}p reachable=${streaming.bestReachableHeight}p " +
                "sabr=${streaming.serverAbrStreamingUrl != null}",
        )
        check(streaming.formats.size > 1) { "expected a full ladder, got ${streaming.formats.size}" }
    }
}
