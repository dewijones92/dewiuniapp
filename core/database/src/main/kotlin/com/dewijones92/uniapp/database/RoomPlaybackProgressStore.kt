package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.playback.PlaybackProgressStore

/**
 * Room-backed [PlaybackProgressStore]. Ignores trivially small positions (so a
 * quick tap doesn't create a resume point) and treats a position near the end
 * as finished — the row is deleted so the item restarts next time.
 */
public class RoomPlaybackProgressStore(
    private val dao: PlaybackProgressDao,
    private val now: () -> Long = System::currentTimeMillis,
) : PlaybackProgressStore {

    override suspend fun resumePositionMs(itemId: MediaItemId): Long? =
        dao.get(itemId.value)?.positionMs

    override suspend fun save(itemId: MediaItemId, positionMs: Long, durationMs: Long?) {
        val finished = durationMs != null && positionMs >= durationMs - NEAR_END_MS
        when {
            positionMs < MIN_SAVE_MS -> Unit // too early to be worth resuming
            finished -> dao.delete(itemId.value)
            else -> dao.upsert(PlaybackProgressEntity(itemId.value, positionMs, durationMs, now()))
        }
    }

    private companion object {
        const val MIN_SAVE_MS = 5_000L
        const val NEAR_END_MS = 15_000L
    }
}
