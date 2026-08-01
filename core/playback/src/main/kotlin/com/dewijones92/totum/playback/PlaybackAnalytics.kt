package com.dewijones92.totum.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.Vitals
import java.io.IOException

/**
 * The detail behind a stall: what is actually being streamed, how fast it is arriving,
 * and what the player is dropping.
 *
 * [PlaybackDiagnostics] can say playback stopped for 24 seconds. It cannot say whether
 * the stream was being fed at 60 kbps or 6 Mbps, which resolution was chosen, or whether
 * a chunk was refused — and those have opposite fixes. This listener is where that comes
 * from, because Media3 only exposes it here.
 *
 * Deliberately not chatty: a completed load is aggregated into running totals rather than
 * logged, since a video issues one every few seconds and a trail of them would bury the
 * events worth reading. Only the things that change the picture — the chosen format, a
 * slow or failed load, dropped frames — write a line.
 */
// The count is Media3's callback surface plus four small formatting helpers; collapsing
// them to satisfy the threshold would only make each callback harder to read.
@Suppress("TooManyFunctions")
@UnstableApi
internal class PlaybackAnalytics : AnalyticsListener {

    private var outstanding = 0
    private var loads = 0L
    private var bytes = 0L

    /**
     * When each in-flight load began, so a HUNG one is visible while it is still hanging.
     *
     * Report 0.1.295 could not be diagnosed without this. It showed six loads outstanding and
     * a sustained ~844kbps against a network the recovery probe had just clocked at 158Mbps —
     * but nothing said whether those six were small chunks arriving slowly (YouTube throttling
     * the stream) or one enormous request the player was sat waiting on (asking for too much at
     * once). Those have opposite fixes, and a completed-load average cannot tell them apart
     * because a load that never completes contributes to it not at all.
     */
    private val startedAtMs = HashMap<Long, Long>()

    /**
     * The last few completed loads, for a throughput figure that describes NOW.
     *
     * It used to be a lifetime running total, which meant one bad sample poisoned every
     * reading that followed — and there is a very common bad sample: a load that spans a
     * pause. Media3 measures a load's duration in wall time, and pausing suspends the
     * loader mid-chunk, so a real report showed 15MB "in" 772 seconds. That dragged the
     * reported rate from 53 Mbps down to 57 kbps and kept it there, turning a healthy
     * connection into a apparent crawl for the rest of the session.
     */
    private val recent = ArrayDeque<Sample>()

    private data class Sample(val bytes: Long, val durationMs: Long)

    /**
     * Which stream is actually playing. "6 qualities available" says nothing about which
     * one was picked, and picking too high is itself a cause of stalling.
     */
    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        evaluation: DecoderReuseEvaluation?,
    ) {
        Vitals.set("playback.videoFormat", format.describe())
        Diag.log("format", "video ${format.describe()}")
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        evaluation: DecoderReuseEvaluation?,
    ) {
        Vitals.set("playback.audioFormat", format.describe())
        Diag.log("format", "audio ${format.describe()}")
    }

    /**
     * Aggregated, then reported as an average — the per-chunk rate is what distinguishes a
     * throttled stream from a fast one that simply has too much to carry.
     */
    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        outstanding = (outstanding - 1).coerceAtLeast(0)
        Vitals.set("playback.loadsOutstanding", outstanding.toString())
        startedAtMs.remove(loadEventInfo.loadTaskId)
        Vitals.set("playback.oldestLoadMs", oldestOutstandingMs().toString())
        loads++
        bytes += loadEventInfo.bytesLoaded
        // Kilobytes, not megabytes: 0.1.295 reported "loadedMb 0" through five minutes of
        // 1080p playback, which reads as "nothing loaded" and is really integer division.
        Vitals.set("playback.loadedKb", (bytes / BYTES_PER_KB).toString())
        Vitals.set("playback.loads", loads.toString())

        if (loadEventInfo.loadDurationMs > SUSPENDED_LOAD_MS) {
            // Not a slow network — a load the player sat on while paused. Said out loud,
            // because a chunk that "took" twelve minutes reads as a catastrophe otherwise,
            // and it is the reason the throughput number used to lie.
            Diag.log(
                "load",
                "${mediaLoadData.trackName()} chunk spanned ${loadEventInfo.loadDurationMs}ms " +
                    "(paused mid-load?) — not counting it towards throughput",
            )
            return
        }
        recent.addLast(Sample(loadEventInfo.bytesLoaded, loadEventInfo.loadDurationMs))
        while (recent.size > RECENT_LOADS) recent.removeFirst()
        Vitals.set("playback.avgLoadKbps", averageKbps().toString())
        // Chunk SIZE is the number that separates the two explanations for a slow stream:
        // many small chunks arriving slowly means the stream is being throttled, while a few
        // huge ones means we asked for too much in one request. The rate alone reads the same.
        Vitals.set("playback.avgChunkKb", averageChunkKb().toString())

        // Periodic rather than per-load: a video issues one every few seconds, and the report
        // buffer is bounded, so this follows the counted-never-silent rule the rest of the
        // trail uses. Every LOAD_SUMMARY_EVERY loads is enough to see a trend.
        if (loads % LOAD_SUMMARY_EVERY == 0L) {
            Diag.log(
                "load",
                "$loads loads, ${bytes / BYTES_PER_KB}KB total, recent " +
                    "~${averageKbps()}kbps in ~${averageChunkKb()}KB chunks, $outstanding in flight",
            )
        }

        // One line only when a single chunk was slow enough to be the problem, so the
        // trail keeps the loads worth seeing and drops the dozens that were fine.
        val kbps = loadEventInfo.kbps()
        if (kbps != null && kbps < SLOW_LOAD_KBPS && loadEventInfo.loadDurationMs > SLOW_LOAD_MS) {
            Diag.warn(
                "load",
                "slow ${mediaLoadData.trackName()} chunk: ${loadEventInfo.bytesLoaded / BYTES_PER_KB}KB in " +
                    "${loadEventInfo.loadDurationMs}ms (~${kbps}kbps)",
            )
        }
    }

    /**
     * Started, with nothing yet to say about it — paired with [onLoadCompleted] so a
     * request that begins and never finishes is visible. A merely slow chunk still
     * completes and is aggregated; a hung one emits nothing at all, which is exactly what
     * a 23-second stall with no load events looks like. Only the outstanding count is
     * kept, so this costs one line per stall rather than one per chunk.
     */
    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        outstanding++
        Vitals.set("playback.loadsOutstanding", outstanding.toString())
        startedAtMs[loadEventInfo.loadTaskId] = eventTime.realtimeMs
        Vitals.set("playback.oldestLoadMs", oldestOutstandingMs().toString())
    }

    /**
     * How long the longest-running unfinished load has been going, in ms; 0 when none are.
     *
     * Read at report time, so a load that is hanging *right now* — the thing a stall actually
     * consists of — shows up. A finished-loads average never can: 0.1.295's one visible load
     * ran 145,750ms and then failed, and it only became visible by failing.
     */
    private fun oldestOutstandingMs(): Long {
        val oldest = startedAtMs.values.minOrNull() ?: return 0
        return (SystemClock.elapsedRealtime() - oldest).coerceAtLeast(0)
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        outstanding = (outstanding - 1).coerceAtLeast(0)
        startedAtMs.remove(loadEventInfo.loadTaskId)
        Vitals.set("playback.oldestLoadMs", oldestOutstandingMs().toString())
        Vitals.add("playback.loadErrors")
        Vitals.set("playback.lastLoadError", "${mediaLoadData.trackName()}: ${error.javaClass.simpleName}")
        Diag.warn(
            "load",
            "${mediaLoadData.trackName()} failed (canceled=$wasCanceled) " +
                "after ${loadEventInfo.loadDurationMs}ms — ${loadEventInfo.uri}",
            error,
        )
    }

    /** Dropped frames separate "the network starved" from "the device could not keep up". */
    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        Vitals.add("playback.droppedFrames", droppedFrames.toLong())
        Diag.log("playback", "dropped $droppedFrames frames over ${elapsedMs}ms")
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) {
        Vitals.set("playback.bandwidthKbps", (bitrateEstimate / BITS_PER_KILOBIT).toString())
    }

    /**
     * Which stream a load belongs to. Higher qualities play a video-only stream merged
     * with a separate audio one, so "the stream stalled" is ambiguous until you know
     * which half — and they are different URLs that can behave differently.
     */
    private fun MediaLoadData.trackName(): String = when (trackType) {
        C.TRACK_TYPE_VIDEO -> "video"
        C.TRACK_TYPE_AUDIO -> "audio"
        C.TRACK_TYPE_TEXT -> "text"
        else -> "track-$trackType"
    }

    /** Mean size of the recent completed loads, in KB — 0 when none have completed yet. */
    private fun averageChunkKb(): Long =
        if (recent.isEmpty()) 0 else recent.sumOf { it.bytes } / recent.size / BYTES_PER_KB

    private fun averageKbps(): Long {
        val totalMs = recent.sumOf { it.durationMs }
        return if (totalMs <= 0) 0 else recent.sumOf { it.bytes } * BITS_PER_BYTE / totalMs
    }

    private fun LoadEventInfo.kbps(): Long? =
        if (loadDurationMs <= 0) null else bytesLoaded * BITS_PER_BYTE / loadDurationMs

    private fun Format.describe(): String = buildString {
        append(codecs ?: sampleMimeType ?: "?")
        if (width > 0 && height > 0) append(" ${width}x$height")
        if (frameRate > 0) append(" @${frameRate.toInt()}fps")
        if (bitrate > 0) append(" ${bitrate / BITS_PER_KILOBIT}kbps")
    }

    private companion object {
        const val BITS_PER_BYTE = 8L
        const val BITS_PER_KILOBIT = 1_000L
        const val BYTES_PER_KB = 1024L

        /** Often enough to show a trend across a stall, rare enough not to crowd the buffer. */
        const val LOAD_SUMMARY_EVERY = 25L

        /** Below this, for long enough to matter, a chunk is worth naming individually. */
        const val SLOW_LOAD_KBPS = 800L
        const val SLOW_LOAD_MS = 1_000L

        /**
         * Recent enough to describe the connection now, long enough not to swing on one
         * chunk — roughly the last minute of a video at typical chunk sizes.
         */
        const val RECENT_LOADS = 20

        /**
         * No genuine chunk takes this long: media3 gives up on a stuck load far sooner, so
         * anything past it is wall time the player spent paused mid-load, not transfer time.
         */
        const val SUSPENDED_LOAD_MS = 60_000L
    }
}
