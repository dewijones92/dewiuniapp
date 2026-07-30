package com.dewijones92.totum.playback

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
        loads++
        bytes += loadEventInfo.bytesLoaded
        Vitals.set("playback.loadedMb", (bytes / BYTES_PER_MB).toString())

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
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        outstanding = (outstanding - 1).coerceAtLeast(0)
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
        const val BYTES_PER_MB = 1024L * 1024L

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
