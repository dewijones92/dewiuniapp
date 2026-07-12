package com.dewijones92.uniapp.playback

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
     * Starts (or restarts) playback of [item]. Plays [localPath] when given
     * (offline download), else streams [MediaItem.mediaUrl]. Any [skipSegments]
     * (e.g. SponsorBlock) are jumped over automatically.
     */
    public fun play(
        item: MediaItem,
        skipSegments: List<SkipSegment> = emptyList(),
        localPath: String? = null,
    )

    /** Toggles play/pause of the current item; no-op when nothing is queued. */
    public fun togglePlayPause()
}

/** What the UI needs to render a player for the current item. */
public data class PlaybackState(
    val itemId: MediaItemId,
    val title: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
) {
    init {
        require(positionMs >= 0) { "positionMs must not be negative" }
        require(durationMs == null || durationMs > 0) { "durationMs must be positive when known" }
    }

    /** 0.0–1.0 when duration is known, else null. */
    public val progress: Float?
        get() = durationMs?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
}
