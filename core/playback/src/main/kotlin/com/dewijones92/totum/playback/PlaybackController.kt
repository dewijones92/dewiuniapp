package com.dewijones92.totum.playback

import androidx.media3.common.Player
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.SubtitleTrack
import com.dewijones92.totum.domain.Chapter
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.SkipSegment
import kotlinx.coroutines.flow.Flow
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
     * Emits when the current stream died in a way a freshly-resolved URL would fix.
     *
     * A streaming URL is a lease, not an address: YouTube signs one for a few hours and
     * then refuses it. Pause overnight, press play in the morning, and every request comes
     * back 403 — which the player reports as a plain source error and retries forever
     * against a URL that will never work again. That is a real report (0.1.170, paused at
     * 23:50, resumed 06:07, seventeen identical 403s).
     *
     * Pillar-agnostic on purpose. Podcast enclosures move and expire too, and whoever
     * listens re-runs whatever produced the item in the first place, which is the same
     * path for both.
     */
    public val streamFailures: Flow<StreamFailure>

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
     * jumped over automatically. [startPositionMs] resumes rather than starting over —
     * how a re-resolved stream picks up where the dead one stopped.
     */
    public fun play(
        item: MediaItem,
        kind: MediaKind = MediaKind.VIDEO,
        skipSegments: List<SkipSegment> = emptyList(),
        localPath: String? = null,
        audioUrl: HttpUrl? = null,
        subtitles: List<SubtitleTrack> = emptyList(),
        startPositionMs: Long = 0,
    )

    /**
     * Chooses the caption language to show, or turns captions off with null.
     *
     * A language rather than a track index: the player already knows which tracks it has,
     * and an index would go stale the moment the item changes — which is exactly when a
     * "keep subtitles on" preference needs to survive.
     */
    public fun setSubtitleLanguage(languageCode: String?)

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

    /** Toggles skipping of near-silent stretches (trims dead air, podcast-style). */
    public fun setSkipSilence(enabled: Boolean)

    /** Lifts quiet audio; remembered per source. Local playback only (not Cast). */
    public fun setVolumeBoost(boost: VolumeBoost)
}

/** What the UI needs to render a player for the current item. */
public data class PlaybackState(
    val itemId: MediaItemId,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    /** Which pillar is playing — lets the UI show whether it's a video or a podcast. */
    val kind: MediaKind = MediaKind.VIDEO,
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
    /** True while the player is buffering (loading/re-buffering) — drives a spinner. */
    val isBuffering: Boolean = false,
    /** Skip (e.g. SponsorBlock) segments for the current item — drawn on the seek bar. */
    val skipSegments: List<SkipSegment> = emptyList(),
    /** Whether silence-skipping (dead-air trimming) is currently on. */
    val skipSilence: Boolean = false,
    val volumeBoost: VolumeBoost = VolumeBoost.OFF,
    /** Chapters of the current item — marked on the seek bar and listed to tap-jump. */
    val chapters: List<Chapter> = emptyList(),
    /** Caption tracks available for the current item; empty when it has none. */
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Language code of the caption track showing, or null for off. */
    val subtitleLanguage: String? = null,
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
