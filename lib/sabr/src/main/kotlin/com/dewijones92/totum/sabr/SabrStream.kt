package com.dewijones92.totum.sabr

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.Vitals

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
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Bytes gathered for [format], keyed by their offset in the whole stream. */
    private val chunks = sortedMapOf<Long, ByteArray>()

    /** Every run declared so far, by header id, because MEDIA parts name their own. */
    private val headers = mutableMapOf<Long, MediaHeader>()

    /** Where the next MEDIA part for each run belongs, since runs interleave. */
    private val writeAt = mutableMapOf<Long, Long>()

    /** The next byte offset we have not yet served to a reader. */
    private var served = 0L
    private var playerTimeMs = 0L
    private var exhausted = false

    /** Counted rather than logged per call: a read happens every few KB and would flood. */
    private var reads = 0
    private var fetches = 0
    private var bytesServed = 0L
    private var totalFetchMs = 0L

    /**
     * Bytes downloaded and thrown away, because a VIDEO request also returns audio and no track
     * bitfield was found that suppresses it. The audio track then fetches that same audio again,
     * so a video played this way costs noticeably more data than it needs to — worth measuring
     * rather than discovering on a phone bill.
     */
    private var bytesDiscarded = 0L

    /** Reads that had to WAIT on the network. The ones a listener hears as a gap. */
    private var readsThatFetched = 0

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
        reads++
        var attempts = 0
        while (attempts < MAX_FETCHES_PER_READ) {
            contiguousFrom(from)?.let { held ->
                bytesServed += held.size
                if (attempts > 0) readsThatFetched++
                return held
            }
            if (exhausted) {
                Diag.log(
                    "sabr",
                    "itag ${format.itag} finished at $from after ${bytesServed}B " +
                        "over $fetches fetches / $reads reads",
                )
                return ByteArray(0)
            }
            fetch()
            attempts++
        }
        // The shape of a stall: the network answered but never with the bytes at this offset.
        Diag.warn(
            "sabr",
            "STUCK: itag ${format.itag} has no bytes at offset $from after $attempts fetches " +
                "(held ${chunks.size} runs at ${chunks.keys.take(HELD_TO_NAME)}, served ${bytesServed}B)",
        )
        return ByteArray(0)
    }

    /** What a report needs to judge whether this felt fast: latency, throughput, and waits. */
    public fun describeProgress(): String {
        val averageMs = if (fetches == 0) 0 else totalFetchMs / fetches
        val wasted = if (bytesServed + bytesDiscarded == 0L) {
            0
        } else {
            bytesDiscarded * PERCENT / (bytesServed + bytesDiscarded)
        }
        return "itag=${format.itag} fetches=$fetches reads=$reads waited=$readsThatFetched " +
            "served=${bytesServed}B discarded=${bytesDiscarded}B ($wasted% wasted) " +
            "avgFetch=${averageMs}ms mediaTime=${playerTimeMs}ms"
    }

    /**
     * Everything we hold that runs on unbroken from [from], or null when we hold nothing there.
     *
     * Coalesces, rather than handing back one stored run at a time. A run that resumes later in
     * the response lands under its own offset key, so without this a caller would be told
     * "nothing" at the join and a fetch would be spent re-asking for bytes already in hand —
     * and a stream can be declared finished while its next bytes are sitting in the map.
     */
    private fun contiguousFrom(from: Long): ByteArray? {
        if (chunks[from] == null) return null
        var at = from
        var joined = ByteArray(0)
        while (true) {
            val next = chunks.remove(at) ?: break
            joined += next
            at += next.size
        }
        return joined.takeIf { it.isNotEmpty() }
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
        val startedAt = clock()
        val response = transport.post(url, body)
        val elapsed = clock() - startedAt
        fetches++
        totalFetchMs += elapsed
        val added = absorb(response)
        bytesDiscarded += (response.size - added).coerceAtLeast(0)
        Vitals.add("sabr.fetches")
        Vitals.add("sabr.fetchMs", elapsed)
        Vitals.add("sabr.bytesKept", added.toLong())
        Vitals.add("sabr.bytesDiscarded", (response.size - added).coerceAtLeast(0).toLong())
        Vitals.set("sabr.lastFetch", "itag ${format.itag} +${added}B in ${elapsed}ms")
        // One line per network round trip, not per read: a fetch covers ~10s of media, so this
        // is a handful of lines a minute and the only place a stall's cause is visible.
        Diag.log(
            "sabr",
            "fetch #$fetches itag ${format.itag} at ${playerTimeMs}ms -> " +
                "${response.size}B response, ${added}B kept, ${elapsed}ms" +
                if (elapsed > SLOW_FETCH_MS) " — SLOW" else "",
        )
        if (added == 0) {
            // Says WHAT came back instead of just that nothing did. An empty result has three
            // very different causes — a refusal, media for a format we did not ask for, or a
            // genuine end — and they are indistinguishable without this.
            exhausted = true
            Vitals.add("sabr.emptyResponses")
            Diag.warn(
                "sabr",
                "itag ${format.itag} got no bytes at ${playerTimeMs}ms from ${response.size}B: " +
                    describe(response),
            )
        }
        playerTimeMs += stepMs
    }

    /**
     * Files away every MEDIA run belonging to [format]. Returns how many bytes were added.
     *
     * **Routed by the header id INSIDE each MEDIA part, not by the last header seen** — this is
     * the whole difficulty of the format and what made video decode to corruption. Runs
     * interleave arbitrarily: measured 2026-07-31 on itag 134 with audio alongside it, a single
     * response went
     *
     * ```
     * MEDIA_HEADER id=3 ; MEDIA(3) ; MEDIA(1) ; MEDIA(1) ; MEDIA(1) ; MEDIA_END(1)
     * MEDIA_HEADER id=4 ; MEDIA(4) ; MEDIA(4) ; MEDIA(3) ; MEDIA_END(3) ; MEDIA(4)
     * ```
     *
     * — header 1's run resuming three parts after header 3 was declared, and header 3's
     * resuming inside header 4's. Attributing bytes to the most recent header therefore splices
     * one format's bytes into another's stream at the wrong offset, which decodes as
     * `Invalid NAL length` rather than failing outright. Audio-only survived it because a single
     * format's runs happen to arrive in order.
     *
     * The leading value is read as a UMP varint, so a header id above 127 works too — ids
     * observed so far are single-digit, which would have hidden a wrong choice indefinitely.
     */
    private fun absorb(response: ByteArray): Int {
        var added = 0
        UmpReader.read(response).parts.forEach { part ->
            when (part.type) {
                UmpPart.MEDIA_HEADER -> remember(MediaHeader.parse(part.payload))
                UmpPart.MEDIA -> added += storeMedia(part.payload)
                else -> Unit
            }
        }
        return added
    }

    private fun remember(header: MediaHeader?) {
        val known = header ?: return
        headers[known.headerId] = known
        // Where this run starts in the whole format; every MEDIA part for it continues from here.
        writeAt[known.headerId] = known.startBytes
        if (known.itag == format.itag) contentLength = known.contentLength
    }

    /** Appends one MEDIA part to whichever run it names. Returns bytes kept. */
    private fun storeMedia(payload: ByteArray): Int {
        val id = UmpVarint.read(payload, 0) ?: return 0
        val header = headers[id.value] ?: return 0
        if (header.itag != format.itag) return 0
        val bytes = payload.copyOfRange(id.next, payload.size)
        if (bytes.isEmpty()) return 0
        val offset = writeAt[id.value] ?: header.startBytes
        writeAt[id.value] = offset + bytes.size
        // Already read past: a reader never goes backwards, so this is spent.
        if (offset < served) return 0
        chunks[offset] = (chunks[offset] ?: ByteArray(0)) + bytes
        return bytes.size
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

    private companion object {
        const val DEFAULT_STEP_MS = 10_000L

        /** A read that cannot be satisfied in this many fetches is a stuck stream, not a slow one. */
        const val MAX_FETCHES_PER_READ = 6
        val PRINTABLE = 32..126
        const val REASON_CHARS = 60

        /** A fetch slower than this is a candidate cause for a gap the listener heard. */
        const val SLOW_FETCH_MS = 3_000L

        /** Enough held offsets to see the shape of a gap without printing a whole map. */
        const val HELD_TO_NAME = 4
        const val PERCENT = 100
    }
}
