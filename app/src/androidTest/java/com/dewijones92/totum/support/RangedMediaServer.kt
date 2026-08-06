package com.dewijones92.totum.support

import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A localhost stand-in that serves media the way a real CDN does: **honouring ranges**.
 *
 * It exists because hand-rolled ones did not, twice. A server that advertises `Accept-Ranges` and
 * then answers a `Range` request with a 200 and the whole body looks fine until the player rebuffers,
 * at which point playback silently never starts — which is what made `TorrentQueuePlaybackTest` flake
 * in CI and nowhere else (2026-08-06). Every ranged read this app does goes through
 * `ChunkedDataSource`, so a stand-in that ignores ranges is not testing the code that ships.
 *
 * [pastTheEndIsEmpty] is the shape that mattered most. Asked for a range starting at or beyond the
 * content, googlevideo can answer with **no bytes rather than a refusal** — and a refusal would at
 * least surface as a load error. Nothing at all is what let a reader ask for the same bytes forever
 * inside a single `read()`, so the load never completed, never cancelled and never errored, and its
 * buffers were never released (report 0.1.359: four videos hard-stalling in their last 45 seconds,
 * 37 loads outstanding and climbing, the heap at 255MB of 256MB).
 */
class RangedMediaServer(
    private val media: ByteArray,
    private val contentType: String = "audio/wav",
    /** True: a range at or past the end answers with no bytes. False: it refuses with 416. */
    private val pastTheEndIsEmpty: Boolean = true,
) : Closeable {

    private val socket = ServerSocket(0)

    /** Every range asked for, so a request past the end of the media is provable. */
    val asked: MutableList<String> = mutableListOf()

    init {
        thread(isDaemon = true, name = "ranged-media-server") { serveUntilClosed() }
    }

    val port: Int get() = socket.localPort

    /**
     * A URL for the media, with the length stated in a `clen` parameter as YouTube's URLs do.
     *
     * `clen` is the path that broke: it lets the data source skip a probe request, and it describes
     * the WHOLE resource — so it has to have the caller's position taken off it, and did not.
     */
    fun url(path: String = "/episode.wav", statesLength: Boolean = true): String =
        "http://127.0.0.1:$port$path" + if (statesLength) "?clen=${media.size}" else ""

    override fun close() {
        runCatching { socket.close() }
    }

    private fun serveUntilClosed() {
        while (!socket.isClosed) {
            val accepted = runCatching { socket.accept() }.getOrNull() ?: return
            thread(isDaemon = true) { runCatching { respond(accepted) } }
        }
    }

    private fun respond(client: Socket) = client.use {
        val input = client.getInputStream().bufferedReader()
        var range: String? = null
        var line = input.readLine()
        while (!line.isNullOrBlank()) {
            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
            line = input.readLine()
        }
        synchronized(asked) { asked += range ?: "(no range)" }

        val out = client.getOutputStream()
        val requested = range?.let(::parse)
        when {
            requested == null -> {
                out.write(head("200 OK", media.size, extra = "Accept-Ranges: bytes\r\n"))
                out.write(media)
            }
            // At or past the end. Both answers are real; the empty one is what hurt.
            requested.first >= media.size ->
                if (pastTheEndIsEmpty) {
                    out.write(head("200 OK", length = 0))
                } else {
                    out.write(head("416 Range Not Satisfiable", length = 0))
                }
            else -> {
                val last = minOf(requested.last, media.size - 1)
                out.write(
                    head(
                        "206 Partial Content",
                        length = last - requested.first + 1,
                        extra = "Content-Range: bytes ${requested.first}-$last/${media.size}\r\n",
                    ),
                )
                out.write(media, requested.first, last - requested.first + 1)
            }
        }
        out.flush()
    }

    /** `bytes=A-B` or `bytes=A-`; the open form runs to the end of the media. */
    private fun parse(header: String): IntRange? {
        val spec = header.substringAfter("bytes=", missingDelimiterValue = "").ifEmpty { return null }
        val from = spec.substringBefore('-').toIntOrNull() ?: return null
        val to = spec.substringAfter('-').toIntOrNull() ?: (media.size - 1)
        return from..to
    }

    private fun head(status: String, length: Int, extra: String = "") =
        (
            "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: $length\r\n" +
                extra +
                "\r\n"
            ).toByteArray()
}
