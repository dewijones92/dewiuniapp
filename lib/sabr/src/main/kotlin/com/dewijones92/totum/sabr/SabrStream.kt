package com.dewijones92.totum.sabr

import com.dewijones92.totum.common.Diag

/** Posts a SABR request body and returns the raw UMP response. */
public fun interface SabrTransport {
    public suspend fun post(url: String, body: ByteArray): ByteArray
}

/**
 * One format of one video, as a sequential byte stream fetched over SABR.
 *
 * SABR is a conversation, not a URL: you say where playback is and it hands you the next
 * segments, framed in UMP and interleaved with whatever other formats it feels like sending.
 * This turns that into the one thing a player wants — bytes in order, from the start — so a
 * Media3 `DataSource` on top has nothing left to understand.
 *
 * **Only the requested itag is kept.** A response carrying audio and video is normal (no track
 * bitfield was found that returns video alone), so anything that is not [format] is dropped by
 * its [MediaHeader]. For audio there IS a bitfield that asks for audio alone, which is why
 * [tracks] exists.
 *
 * Progress is driven by `player_time_ms`, because that is what the server actually responds to:
 * `buffered_ranges` alone advanced twice and then stalled, while the same request with a larger
 * `player_time_ms` reached byte 8761825 instead of 1271335. So each fetch asks from a little
 * further on than the last, and the wall clock of the media — not our byte count — is what
 * moves.
 */
public class SabrStream(
    private val url: String,
    private val ustreamerConfig: ByteArray,
    private val format: SabrFormat,
    /** Whether [format] is the audio or the video track — which request field it belongs in. */
    private val kind: SabrTrackKind,
    private val transport: SabrTransport,
    /** How much media time to advance per fetch. Segments observed at ~10s for audio. */
    private val stepMs: Long = DEFAULT_STEP_MS,
) {
    /** Bytes gathered for [format], keyed by their offset in the whole stream. */
    private val chunks = sortedMapOf<Long, ByteArray>()

    /** The next byte offset we have not yet served to a reader. */
    private var served = 0L
    private var playerTimeMs = 0L
    private var exhausted = false

    /** Total length of this format, once a header has declared it. */
    public var contentLength: Long? = null
        private set

    /**
     * Bytes starting at [from], or empty when the stream is finished.
     *
     * Fetches as needed. Returns what is contiguously available rather than a fixed size,
     * because SABR decides how much to send and pretending otherwise would mean buffering
     * whole megabytes to satisfy an arbitrary request length.
     */
    public suspend fun read(from: Long): ByteArray {
        served = from
        var attempts = 0
        while (attempts < MAX_FETCHES_PER_READ) {
            contiguousFrom(from)?.let { return it }
            if (exhausted) return ByteArray(0)
            fetch()
            attempts++
        }
        Diag.warn("sabr", "no bytes at offset $from for itag ${format.itag} after $attempts fetches")
        return ByteArray(0)
    }

    /** The run of bytes we hold starting exactly at [from], or null when we hold none. */
    private fun contiguousFrom(from: Long): ByteArray? {
        val chunk = chunks[from] ?: return null
        chunks.remove(from)
        return chunk.takeIf { it.isNotEmpty() }
    }

    private suspend fun fetch() {
        val body = VideoPlaybackAbrRequest(
            ustreamerConfig = ustreamerConfig,
            playerTimeMs = playerTimeMs,
            audio = format.takeIf { kind == SabrTrackKind.AUDIO },
            video = format.takeIf { kind == SabrTrackKind.VIDEO },
            // Audio alone is the one selection the server honours, and it is a tenth of the
            // bytes; asking for video means accepting audio alongside it.
            tracks = if (kind == SabrTrackKind.AUDIO) SabrTracks.AUDIO_ONLY else SabrTracks.AUDIO_AND_VIDEO,
        ).encode()
        val response = transport.post(url, body)
        val added = absorb(response)
        if (added == 0) {
            // Says WHAT came back instead of just that nothing did. An empty result has three
            // very different causes — a refusal, media for a format we did not ask for, or a
            // genuine end — and they are indistinguishable without this.
            exhausted = true
            Diag.warn(
                "sabr",
                "itag ${format.itag} got no bytes at ${playerTimeMs}ms from ${response.size}B: " +
                    describe(response),
            )
        }
        playerTimeMs += stepMs
    }

    /** Files away every MEDIA run belonging to [format]. Returns how many bytes were added. */
    private fun absorb(response: ByteArray): Int {
        var header: MediaHeader? = null
        var offset = 0L
        var added = 0
        UmpReader.read(response).parts.forEach { part ->
            when (part.type) {
                UmpPart.MEDIA_HEADER -> {
                    val parsed = MediaHeader.parse(part.payload)
                    header = parsed
                    offset = parsed?.startBytes ?: 0
                    if (parsed?.itag == format.itag) contentLength = parsed.contentLength
                }
                UmpPart.MEDIA -> {
                    val bytes = mediaFor(header, part.payload)
                    added += store(offset, bytes)
                    offset += bytes.size
                }
                else -> Unit
            }
        }
        return added
    }

    /** What a response actually contained, for when it contained nothing we wanted. */
    private fun describe(response: ByteArray): String {
        val parts = UmpReader.read(response).parts
        val itags = parts.filter { it.type == UmpPart.MEDIA_HEADER }
            .mapNotNull { MediaHeader.parse(it.payload)?.itag }
            .distinct()
        val reasons = parts.filter { it.type == UmpPart.SABR_ERROR || it.type == UmpPart.RELOAD_PLAYER_RESPONSE }
            .map { part -> part.payload.decodeToString().filter { it.code in PRINTABLE }.take(REASON_CHARS) }
        return "parts=${parts.map { it.name }.distinct()} itags=$itags reasons=$reasons"
    }

    /** The media in [payload] when it belongs to our format, else nothing. */
    private fun mediaFor(header: MediaHeader?, payload: ByteArray): ByteArray =
        if (header?.itag != format.itag || payload.isEmpty()) {
            ByteArray(0)
        } else {
            // MEDIA is prefixed with a single byte that is not media.
            payload.copyOfRange(1, payload.size)
        }

    /** Files [bytes] at [offset] unless a reader has already moved past it. */
    private fun store(offset: Long, bytes: ByteArray): Int {
        if (bytes.isEmpty() || offset < served) return 0
        chunks[offset] = (chunks[offset] ?: ByteArray(0)) + bytes
        return bytes.size
    }

    private companion object {
        const val DEFAULT_STEP_MS = 10_000L

        /** A read that cannot be satisfied in this many fetches is a stuck stream, not a slow one. */
        const val MAX_FETCHES_PER_READ = 6
        val PRINTABLE = 32..126
        const val REASON_CHARS = 60
    }
}
