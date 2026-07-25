package com.dewijones92.totum.playback

import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Remembers how far into each item playback reached, so anything — a podcast
 * episode or a video — resumes where it was left, and every list can show whether
 * an item is unplayed, part-way or finished. One seam for both pillars; the
 * controller saves as it plays and restores on the next play.
 */
public interface PlaybackProgressStore {

    /** Position to resume [itemId] at, or null to start from the beginning. */
    public suspend fun resumePositionMs(itemId: MediaItemId): Long?

    /**
     * Records playback progress for [itemId]. Implementations may treat a
     * position near [durationMs] as finished — which marks the item played, and
     * still restarts it from the beginning next time — and may ignore trivially
     * small positions.
     */
    public suspend fun save(itemId: MediaItemId, positionMs: Long, durationMs: Long?)

    /**
     * Play state of every item that has one. Items absent from the map are
     * [PlayState.Unplayed], so a list reads this once rather than querying per row.
     */
    public fun observeStates(): Flow<Map<MediaItemId, PlayState>>

    /**
     * Marks [itemId] played or unplayed by hand — AntennaPod's most-used row action.
     * Marking unplayed clears any resume point, so the item starts clean.
     */
    public suspend fun setPlayed(itemId: MediaItemId, played: Boolean)
}

/** Default store that remembers nothing — playback still works, resume just no-ops. */
public object NoOpPlaybackProgressStore : PlaybackProgressStore {
    override suspend fun resumePositionMs(itemId: MediaItemId): Long? = null
    override suspend fun save(itemId: MediaItemId, positionMs: Long, durationMs: Long?): Unit = Unit
    override fun observeStates(): Flow<Map<MediaItemId, PlayState>> = flowOf(emptyMap())
    override suspend fun setPlayed(itemId: MediaItemId, played: Boolean): Unit = Unit
}
