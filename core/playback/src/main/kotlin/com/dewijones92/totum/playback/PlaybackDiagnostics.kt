package com.dewijones92.totum.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.Vitals
import androidx.media3.common.MediaItem as Media3MediaItem

/**
 * Records what playback actually did — errors, stalls, transitions — as breadcrumbs and
 * running [Vitals].
 *
 * It exists because none of this was observable. There was no [Player.Listener.onPlayerError]
 * anywhere in the app, so a failed stream was completely silent: the UI sat there and a
 * crash report carried no hint. Buffering was equally invisible — `isBuffering` drove a
 * spinner but nothing counted or timed the stalls, which is why "is it buffering?" could
 * only be answered by watching the screen.
 *
 * A separate listener from the one that publishes [PlaybackState] on purpose: observing is
 * not the same job as mapping state, and this way logging can never break playback.
 */
internal class PlaybackDiagnostics(
    private val player: () -> Player?,
    private val now: () -> Long = System::currentTimeMillis,
) : Player.Listener {

    private var stalledSince: Long? = null

    override fun onPlayerError(error: PlaybackException) {
        Vitals.add("playback.errors")
        Vitals.set("playback.lastError", "${error.errorCodeName}: ${error.message}")
        Diag.warn(
            "playback",
            "ERROR ${error.errorCodeName} (${error.errorCode}) at ${position()} — ${describeItem()}",
            error,
        )
    }

    /**
     * Times each stall rather than just noting it. A duration is what distinguishes a
     * normal start-up buffer from the repeated mid-item stalls that read as "buffering".
     */
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                stalledSince = now()
                Vitals.add("playback.stalls")
                val kbps = PlaybackVitals.kbps()
                val outstanding = Vitals.snapshot()["playback.loadsOutstanding"]
                Diag.log(
                    "playback",
                    "buffering at ${position()}" + (kbps?.let { " (was ~${it}kbps" } ?: " (") +
                        ", $outstanding load(s) in flight)",
                )
            }
            Player.STATE_READY -> {
                val waited = stalledSince?.let { now() - it }
                stalledSince = null
                if (waited != null) {
                    Vitals.add("playback.bufferingMs", waited)
                    // The throughput at the moment it recovered is what separates a
                    // throttled stream from a connection that simply cannot carry 1080p.
                    val kbps = PlaybackVitals.kbps()
                    kbps?.let { Vitals.set("playback.lastRecoveryKbps", it.toString()) }
                    Diag.log(
                        "playback",
                        "ready after ${waited}ms at ${position()}" +
                            (kbps?.let { " (throughput ~${it}kbps)" } ?: ""),
                    )
                }
            }
            Player.STATE_ENDED -> reportEnd()
            Player.STATE_IDLE -> Diag.log("playback", "idle")
        }
    }

    /**
     * Says whether a video ended where it was SUPPOSED to.
     *
     * "ended" alone cannot be judged: a stream that stops short looks exactly like a short video,
     * and the queue advances either way. So the end is reported against the duration, and a
     * finish more than [EARLY_END_TOLERANCE_MS] short of it is named as early — which is the
     * symptom to look for on the SABR path, where a stalled fetch used to be taken for an end.
     */
    private fun reportEnd() {
        val player = player()
        val duration = player?.duration?.takeIf { it > 0 }
        val at = player?.currentPosition ?: 0
        val shortBy = duration?.minus(at) ?: 0
        if (duration != null && shortBy > EARLY_END_TOLERANCE_MS) {
            Vitals.add("playback.earlyEnds")
            Diag.warn(
                "playback",
                "ENDED EARLY at ${at}ms of ${duration}ms — ${shortBy}ms short (${percent(at, duration)}%) " +
                    "— ${describeItem()}",
            )
        } else {
            Diag.log("playback", "ended at ${at}ms of ${duration ?: -1}ms — ${describeItem()}")
        }
    }

    private fun percent(at: Long, duration: Long) = at * PERCENT / duration

    /** The reason matters: an automatic advance and a user tap look identical without it. */
    override fun onMediaItemTransition(mediaItem: Media3MediaItem?, reason: Int) {
        stalledSince = null
        Vitals.add("playback.transitions")
        Diag.log("playback", "transition (${reasonName(reason)}) -> ${mediaItem?.mediaId ?: "nothing"}")
    }

    /**
     * "Not playing" is three different things — paused, stalled, finished — and Media3
     * reports them all here. Saying which matters: the first test run logged "paused"
     * in the middle of a 15-second stall, which reads like the user did it.
     */
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val why = when {
            isPlaying -> "playing"
            player()?.playWhenReady == true -> "not advancing (wants to play)"
            else -> "paused"
        }
        Diag.log("playback", "$why at ${position()}")
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        // Seeks only: an automatic period transition fires this on every item change and
        // would bury the trail in lines that onMediaItemTransition already covers.
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        Diag.log("playback", "seek ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms")
    }

    private fun position(): String = player()?.let { "${it.currentPosition}ms" } ?: "?"

    private fun describeItem(): String {
        val current = player()?.currentMediaItem ?: return "nothing playing"
        return "${current.mediaId} \"${current.mediaMetadata.title}\""
    }

    private fun reasonName(reason: Int): String = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "auto"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "seek"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "playlist-changed"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "repeat"
        else -> "reason-$reason"
    }

    private companion object {
        /**
         * How near the duration counts as a proper finish. Generous, because a container's
         * declared duration and its last sample rarely agree to the millisecond, and crying
         * "early" on every ordinary ending would make the warning worthless.
         */
        const val EARLY_END_TOLERANCE_MS = 5_000L
        const val PERCENT = 100
    }
}
