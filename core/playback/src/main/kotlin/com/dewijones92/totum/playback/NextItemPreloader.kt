package com.dewijones92.totum.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import com.dewijones92.totum.common.Diag

/**
 * Holds the first [BufferBudget.PRELOAD_MS] of what is coming next, so a track change is not a wait.
 *
 * Dewi, 2026-08-02: *"just 30 seconds of future to be loaded right??"* — and Wi-Fi only, which the
 * app decides before it ever nominates anything. Nothing here spends data on its own initiative.
 *
 * `ExoPlayer.PreloadConfiguration` cannot do this job: it preloads the next item in the PLAYER'S
 * PLAYLIST, and the queue plays one item at a time because it owns advancing. This holds sources
 * outside any playlist, which is the shape that fits.
 *
 * Its own class because holding bytes for later is a separate job from playing them, and because
 * that separation is what makes [releaseIfPlaying] obvious — it was missing while this lived
 * inside the session callback, so a preloaded item's bytes were held twice on the same heap.
 */
@UnstableApi
internal class NextItemPreloader(
    private val context: Context,
    /** The player's own factory: a source preloaded by a different one is discarded, not reused. */
    private val sourceFactory: () -> MediaSource.Factory,
) {
    private var manager: DefaultPreloadManager? = null

    /** What is currently held, so nominating something else releases it. */
    private var held: MediaItem? = null

    @OptIn(UnstableApi::class)
    fun hold(uri: String) {
        if (held?.localConfiguration?.uri?.toString() == uri) return
        val preloader = manager ?: build().also { manager = it }
        held?.let { preloader.remove(it) }
        val item = MediaItem.fromUri(uri)
        held = item
        preloader.add(item, 0)
        preloader.invalidate()
        Diag.log("preload", "holding the first ${BufferBudget.PRELOAD_MS}ms of ${uri.take(URL_CHARS)}")
    }

    /**
     * Drops the held copy of whatever just started playing.
     *
     * Once an item is playing the player loads it itself, so anything still held for it is a SECOND
     * copy of the same bytes on the same heap. It used to be held until the next nomination — which,
     * for the last item in a queue, never comes. That doubling was live in 0.1.346, the build that
     * died of `OutOfMemoryError` (2026-08-06).
     */
    @OptIn(UnstableApi::class)
    fun releaseIfPlaying(item: MediaItem?) {
        val holding = held ?: return
        // Either source, because a MediaItem crossing the session boundary does not always carry
        // its localConfiguration — requestMetadata is what survives.
        val playing = item?.uriOrNull()
        val heldUri = holding.uriOrNull()
        if (playing == null || playing != heldUri) {
            // Said out loud: this is a silent no-op otherwise, and a silent no-op here means the
            // bytes stay held. The instrumented test caught exactly that.
            Diag.log("preload", "still holding $heldUri — what started is $playing")
            return
        }
        manager?.remove(holding)
        held = null
        Diag.log("preload", "released the held copy of ${playing.take(URL_CHARS)} — it is playing now")
    }

    private fun MediaItem.uriOrNull(): String? =
        (localConfiguration?.uri ?: requestMetadata.mediaUri)?.toString()

    @OptIn(UnstableApi::class)
    private fun build(): DefaultPreloadManager =
        DefaultPreloadManager.Builder(context) { _: Int ->
            DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(BufferBudget.PRELOAD_MS * MICROS_PER_MS)
        }
            .setMediaSourceFactory(sourceFactory())
            // Its own ceiling, or it takes Media3's 137.5MB preload default on top of whatever the
            // player already holds — see [BufferBudget].
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setTargetBufferBytes(BufferBudget.PRELOAD_BYTES)
                    .build(),
            )
            // No setPreloadLooper: the Context constructor supplies one and setting it again throws.
            .build()

    private companion object {
        const val MICROS_PER_MS = 1_000L
        const val URL_CHARS = 80
    }
}
