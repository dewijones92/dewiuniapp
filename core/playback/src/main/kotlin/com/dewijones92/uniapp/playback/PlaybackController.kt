package com.dewijones92.uniapp.playback

import androidx.media3.common.Player
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SkipSegment
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's single playback seam. Both pillars play through this: a podcast
 * episode and an extracted video are just [MediaItem]s whose [MediaItem.mediaUrl]
 * points at something playable.
 */
public interface PlaybackController {

    /** Null when nothing has been queued this session. */
    public val state: StateFlow<PlaybackState?>

    /**
     * The underlying player, for binding a video surface (the one place the UI
     * renders video). Null until connected, or for backends with no video
     * output (e.g. the fake). Audio-only items simply produce no video frames.
     */
    public val player: Player?

    /**
     * Starts (or restarts) playback of [item]. Plays [localPath] when given
     * (offline download), else streams [MediaItem.mediaUrl]. When [audioUrl] is
     * given, [MediaItem.mediaUrl] is a video-only stream and [audioUrl] its
     * separate audio track — the two are merged for playback (how higher-than-
     * muxed video qualities stream). Any [skipSegments] (e.g. SponsorBlock) are
     * jumped over automatically.
     */
    public fun play(
        item: MediaItem,
        skipSegments: List<SkipSegment> = emptyList(),
        localPath: String? = null,
        audioUrl: HttpUrl? = null,
    )

    /** Toggles play/pause of the current item; no-op when nothing is queued. */
    public fun togglePlayPause()

    /** Seeks the current item to [positionMs] (clamped to [0, duration]). */
    public fun seekTo(positionMs: Long)

    /** Jumps back by the configured increment (10s). */
    public fun seekBackward()

    /** Jumps forward by the configured increment (30s). */
    public fun seekForward()

    /** Sets playback speed (e.g. 1.0, 1.5, 2.0); clamped to a sensible range. */
    public fun setSpeed(speed: Float)
}

/** What the UI needs to render a player for the current item. */
public data class PlaybackState(
    val itemId: MediaItemId,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    /** The item's description / show notes, when known. Shown on the full player. */
    val description: String? = null,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val speed: Float,
    /**
     * True when the current item has a video track to show (vs a podcast /
     * audio-only track). Known from the track list, so it's set before any
     * frame decodes — the UI must show the surface for decoding to begin.
     */
    val hasVideo: Boolean = false,
    /**
     * width/height of the video once decoding reports it; null until then (or
     * for audio). The UI defaults to 16:9 while this is unknown.
     */
    val videoAspectRatio: Float? = null,
    /**
     * True once the current item has played to its end (the player reached the
     * ended state). Drives "up next" autoplay; clears when the next item starts.
     */
    val hasEnded: Boolean = false,
) {
    init {
        require(positionMs >= 0) { "positionMs must not be negative" }
        require(durationMs == null || durationMs > 0) { "durationMs must be positive when known" }
        require(speed > 0) { "speed must be positive" }
        require(videoAspectRatio == null || videoAspectRatio > 0) {
            "videoAspectRatio must be positive when present"
        }
    }

    /** 0.0–1.0 when duration is known, else null. */
    public val progress: Float?
        get() = durationMs?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
}
