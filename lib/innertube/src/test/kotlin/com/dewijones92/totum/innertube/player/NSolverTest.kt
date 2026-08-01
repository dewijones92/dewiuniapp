package com.dewijones92.totum.innertube.player

import com.dewijones92.totum.common.HttpUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether an age-restricted video plays or 403s.
 *
 * All of this is arithmetic on URLs, so it belongs on the JVM where it can be checked in
 * milliseconds — the JS engine behind [NSolver] is the only part that needs a device.
 */
class NSolverTest {

    private fun format(itag: Int, url: String?) = PlayableFormat(
        itag = itag,
        mimeType = "video/mp4",
        height = 720,
        bitrate = 1_000,
        url = url?.let(HttpUrl::of),
    )

    private val playerUrl = "https://www.youtube.com/s/player/bed7a914/player_ias.vflset/en_US/base.js"

    @Test
    fun `the n parameter is read from anywhere in the query`() {
        assertEquals("abc123", HttpUrl.of("https://x.test/v?n=abc123").nParameter())
        assertEquals("abc123", HttpUrl.of("https://x.test/v?itag=140&n=abc123&sig=z").nParameter())
        assertNull(HttpUrl.of("https://x.test/v?itag=140&sig=z").nParameter())
    }

    /**
     * A URL that merely CONTAINS "n=" inside another parameter must not be mistaken for one.
     * Real googlevideo URLs carry `ns`, `mn`, `sn` and `lsig`, so an unanchored match would
     * corrupt the signature and produce a 403 that looks exactly like an unsolved parameter.
     */
    @Test
    fun `a parameter merely ending in n is not the n parameter`() {
        assertNull(HttpUrl.of("https://x.test/v?ns=1&mn=aa&sn=bb").nParameter())
    }

    @Test
    fun `replacing n leaves every other parameter untouched`() {
        val rewritten = HttpUrl.of("https://x.test/v?itag=140&n=RAW&sig=keepme").withN("SOLVED")
        assertEquals("https://x.test/v?itag=140&n=SOLVED&sig=keepme", rewritten?.value)
    }

    @Test
    fun `solved formats get the deobfuscated url`() = runTest {
        val data = StreamingData(formats = listOf(format(140, "https://x.test/v?n=RAW")))
        val solved = data.withSolvedN({ _, _ -> mapOf("RAW" to "OK") }, playerUrl)

        assertEquals("https://x.test/v?n=OK", solved.formats.single().url?.value)
    }

    /**
     * The point of the whole design. An unsolved parameter yields a URL that is CERTAIN to
     * 403, so offering it to the player converts a clean "no playable formats" into a stall
     * part-way through a video. Passing the raw value through — which is what NewPipe's
     * solver does on failure — is the specific bug being designed out here.
     */
    @Test
    fun `a format whose n could not be solved is dropped, never passed through raw`() = runTest {
        val data = StreamingData(
            formats = listOf(
                format(140, "https://x.test/a?n=SOLVABLE"),
                format(251, "https://x.test/b?n=HOPELESS"),
            ),
        )
        val solved = data.withSolvedN({ _, _ -> mapOf("SOLVABLE" to "OK") }, playerUrl)

        assertEquals(listOf(140), solved.formats.map { it.itag })
        assertTrue(solved.formats.none { it.url?.value?.contains("HOPELESS") == true })
    }

    /** Formats YouTube will only serve over SABR have no URL at all and must survive. */
    @Test
    fun `a format with no url is untouched`() = runTest {
        val data = StreamingData(formats = listOf(format(299, url = null)))
        assertEquals(1, data.withSolvedN({ _, _ -> emptyMap() }, playerUrl).formats.size)
    }

    /** Nothing to solve means nothing to ask, so the solver is never invoked. */
    @Test
    fun `no n parameters means the solver is not called at all`() = runTest {
        val data = StreamingData(formats = listOf(format(140, "https://x.test/v?itag=140")))
        var asked = false
        val solved = data.withSolvedN(
            { _, _ ->
                asked = true
                emptyMap()
            },
            playerUrl,
        )

        assertEquals(1, solved.formats.size)
        assertEquals(false, asked)
    }

    /**
     * The solver runs a JS engine over a 2.9MB script in another process; it can throw.
     * When it does, resolution must fall through to "nothing playable" rather than take the
     * playback path down with it.
     */
    @Test
    fun `a solver that throws drops the formats instead of propagating`() = runTest {
        val data = StreamingData(formats = listOf(format(140, "https://x.test/v?n=RAW")))
        val solved = data.withSolvedN({ _, _ -> error("qjs died") }, playerUrl)

        assertTrue(solved.formats.isEmpty())
    }

    /** One request for a parameter shared by several formats — a solve is seconds, not free. */
    @Test
    fun `a repeated n parameter is only asked about once`() = runTest {
        val data = StreamingData(
            formats = listOf(format(140, "https://x.test/a?n=SAME"), format(251, "https://x.test/b?n=SAME")),
        )
        var asked = emptyList<String>()
        data.withSolvedN(
            { challenges, _ ->
                asked = challenges
                mapOf("SAME" to "OK")
            },
            playerUrl,
        )

        assertEquals(listOf("SAME"), asked)
    }
}
