package com.dewijones92.uniapp.playback

import com.dewijones92.uniapp.domain.MediaItemId

/**
 * Remembers how far into each item playback reached, so anything — a podcast
 * episode or a video — resumes where it was left. One seam for both pillars;
 * the controller saves as it plays and restores on the next play.
 */
public interface PlaybackProgressStore {

    /** Position to resume [itemId] at, or null to start from the beginning. */
    public suspend fun resumePositionMs(itemId: MediaItemId): Long?

    /**
     * Records playback progress for [itemId]. Implementations may treat a
     * position near [durationMs] as "finished" (so it resumes from the start
     * next time) and may ignore trivially small positions.
     */
    public suspend fun save(itemId: MediaItemId, positionMs: Long, durationMs: Long?)
}

/** Default store that remembers nothing — playback still works, resume just no-ops. */
public object NoOpPlaybackProgressStore : PlaybackProgressStore {
    override suspend fun resumePositionMs(itemId: MediaItemId): Long? = null
    override suspend fun save(itemId: MediaItemId, positionMs: Long, durationMs: Long?): Unit = Unit
}
