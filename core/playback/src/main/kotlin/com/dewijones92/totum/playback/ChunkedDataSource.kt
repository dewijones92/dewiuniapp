package com.dewijones92.totum.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.dewijones92.totum.common.Diag

/**
 * Fetches a stream as a series of bounded range requests instead of one open-ended GET.
 *
 * **This is the fix for the buffering.** YouTube throttles a non-ranged `videoplayback`
 * request to roughly playback rate. Measured against one real stream: an open-ended GET
 * sustained 122–178 KB/s while ranged requests for the same bytes got 414 KB/s–1.1 MB/s.
 * The 1080p H.264 stream needs 166 KB/s — inside the throttled band — so the player was
 * being fed at almost exactly real time with no headroom. It covered about seven seconds
 * on the opening burst and then stalled for twenty-odd, over and over, on both a Pixel 7
 * and the emulator at the same position. Nothing about that is bandwidth: the connection
 * carried ten times what the stream needed the moment the request was bounded.
 *
 * Every serious YouTube client does this; it is why they do not stall.
 *
 * The wrapper is transparent. ExoPlayer opens once and reads to the end; each time a
 * chunk is exhausted the next range is opened underneath, so nothing above needs to know.
 */
// The count is DataSource's interface plus the small helpers that decide a chunk's bounds;
// splitting it would scatter the one thing this class knows, which is how to range a request.
@Suppress("TooManyFunctions")
@UnstableApi
internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
) : DataSource {

    private var spec: DataSpec? = null
    private var position = 0L

    /** Bytes of the caller's request still to serve; [UNKNOWN_LENGTH] when unknown. */
    private var remaining = UNKNOWN_LENGTH

    /** Bytes left in the range currently open upstream. */
    private var chunkRemaining = 0L
    private var chunkOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        position = dataSpec.position
        remaining = when {
            dataSpec.length != UNKNOWN_LENGTH -> dataSpec.length
            // Ask the URL before asking the server. YouTube puts the content length in a
            // `clen` parameter, so a probe request is both slower and — for the ANDROID
            // client's URLs — fatal: they answer an UNBOUNDED GET with 403, which is exactly
            // what a probe is. That broke every video resolved through the InnerTube
            // fallback on Dewi's phone, retrying ~20 times in 13 seconds and never playing.
            else -> declaredLength(dataSpec.uri) ?: probeLength(dataSpec)
        }
        openNextChunk()
        return remaining
    }

    /**
     * The length YouTube states in the URL's `clen`, or null when it says nothing.
     *
     * Free — no request at all — and it sidesteps the probe entirely for the streams we
     * actually play.
     */
    private fun declaredLength(uri: Uri): Long? =
        runCatching { uri.getQueryParameter("clen")?.toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 0 }

    /**
     * Asks how long the resource is, then closes without reading it.
     *
     * A bounded request answers 206 with only that range's size, so the total has to come
     * from an unbounded one. It is closed immediately, so the throttling that applies to
     * an open-ended GET never gets the chance to matter.
     */
    private fun probeLength(dataSpec: DataSpec): Long {
        val length = runCatching { upstream.open(dataSpec) }.getOrElse { failure ->
            // An HTTP status is the resource answering, and answering "no" — rethrow it so
            // the player sees the real 403 rather than a second, identical failure from the
            // chunk that would follow. Recovery keys off that status; masking it behind a
            // fallback doubled every request on the dead-URL path and hid the reason.
            if (failure is HttpDataSource.InvalidResponseCodeException) throw failure
            Diag.warn("chunked", "could not measure length; falling back to one request", failure)
            return UNKNOWN_LENGTH
        }
        runCatching { upstream.close() }
        return length
    }

    private fun openNextChunk() {
        val current = spec ?: return
        if (remaining == 0L) return
        val size = if (remaining == UNKNOWN_LENGTH) chunkBytes else minOf(chunkBytes, remaining)
        chunkRemaining = upstream.open(current.subrange(position - current.position, size))
        chunkOpen = true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (!chunkOpen) openNextChunk()

        val read = upstream.read(buffer, offset, boundedLength(length))
        if (read == C.RESULT_END_OF_INPUT) {
            // The chunk ended, not the resource: close it and continue from where it
            // stopped. Only an unknown total can genuinely end here.
            closeChunk()
            if (remaining == UNKNOWN_LENGTH || remaining == 0L) return C.RESULT_END_OF_INPUT
            openNextChunk()
            return read(buffer, offset, length)
        }

        position += read
        chunkRemaining -= read
        if (remaining != UNKNOWN_LENGTH) remaining -= read
        if (chunkRemaining == 0L) closeChunk()
        return read
    }

    /** Never read past the current range, or the next chunk would start at the wrong offset. */
    private fun boundedLength(length: Int): Int =
        if (chunkRemaining == UNKNOWN_LENGTH) length else minOf(length.toLong(), chunkRemaining).toInt()

    private fun closeChunk() {
        if (!chunkOpen) return
        chunkOpen = false
        runCatching { upstream.close() }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeChunk()
        spec = null
    }

    /** Wraps another factory so every source it makes fetches in ranges. */
    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstream.createDataSource(), chunkBytes)
    }

    private companion object {
        /** C.LENGTH_UNSET is an Int; every length here is a Long. */
        const val UNKNOWN_LENGTH = -1L

        /**
         * Big enough that the per-request overhead is noise, small enough that the
         * throttle never engages. Roughly ten seconds of a 1080p stream.
         */
        const val DEFAULT_CHUNK_BYTES = 2L * 1024 * 1024
    }
}
