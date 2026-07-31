package com.dewijones92.totum.sabr

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches real media from YouTube over SABR, with the real code.
 *
 * **Opt-in.** Skipped unless `/tmp/sabr-live/` holds the inputs, so CI never touches the
 * network and this cannot fail for reasons outside the repo. Populate it with:
 *   `url` — the player response's `serverAbrStreamingUrl`
 *   `config` — `videoPlaybackUstreamerConfig`, base64url-DECODED
 *   `format` — `itag lastModified xtags` on one line (`-` for absent xtags)
 *
 * It exists because unit tests with a fake transport cannot tell you that YouTube accepts what
 * we send. The one thing worth knowing about this code is whether the bytes come back and
 * decode, and only the real server can answer that.
 */
class SabrLiveStreamTest {

    private val inputs = File("/tmp/sabr-live")

    private class RealTransport : SabrTransport {
        override suspend fun post(url: String, body: ByteArray): ByteArray {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/x-protobuf")
                setRequestProperty("User-Agent", ANDROID_UA)
            }
            connection.outputStream.use { it.write(body) }
            return connection.inputStream.use { it.readBytes() }
        }

        private companion object {
            const val TIMEOUT_MS = 40_000
            const val ANDROID_UA = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
        }
    }

    @Test
    fun `fetches a run of real audio segments in order`() = runTest {
        assumeTrue("populate /tmp/sabr-live to run this", inputs.isDirectory)
        val spec = File(inputs, "format").readText().trim().split(" ")
        val stream = SabrStream(
            url = File(inputs, "url").readText().trim(),
            ustreamerConfig = File(inputs, "config").readBytes(),
            format = SabrFormat(spec[0].toInt(), spec[1].toLong(), spec.getOrNull(2)?.takeIf { it != "-" }),
            kind = SabrTrackKind.AUDIO,
            transport = RealTransport(),
        )

        val collected = ArrayList<Byte>()
        var offset = 0L
        repeat(FETCHES) {
            val chunk = stream.read(offset)
            if (chunk.isEmpty()) return@repeat
            collected += chunk.toList()
            offset += chunk.size
        }

        val out = File("/tmp/sabr-live/out.webm")
        out.writeBytes(collected.toByteArray())
        println("SABR fetched ${collected.size} bytes to ${out.absolutePath}")

        assertTrue("expected media bytes", collected.size > 10_000)
        // EBML magic: these are a real WebM container, not an error page.
        assertTrue(
            "expected a WebM header",
            collected.take(4) == listOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte()),
        )
    }

    private companion object {
        const val FETCHES = 6
    }
}
