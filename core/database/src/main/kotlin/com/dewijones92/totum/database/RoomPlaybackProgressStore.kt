package com.dewijones92.totum.database

import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayState
import com.dewijones92.totum.playback.PlaybackProgressStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [PlaybackProgressStore]. Ignores trivially small positions (so a
 * quick tap doesn't create a resume point) and treats a position near the end as
 * finished.
 *
 * A finished item keeps its row, marked completed, rather than being deleted: that
 * row is what lets a list say "played" instead of inferring it from an absence. It
 * still restarts from the beginning, because [resumePositionMs] reports no position
 * for a completed item — restarting is a playback rule, not a storage side effect.
 */
public class RoomPlaybackProgressStore(
    private val dao: PlaybackProgressDao,
    private val now: () -> Long = System::currentTimeMillis,
) : PlaybackProgressStore {

    override suspend fun resumePositionMs(itemId: MediaItemId): Long? =
        dao.get(itemId.value)?.takeIf { it.completedAtEpochMs == null }?.positionMs

    override suspend fun save(itemId: MediaItemId, positionMs: Long, durationMs: Long?) {
        val finished = durationMs != null && positionMs >= durationMs - tailFor(durationMs)
        when {
            // Too early to be worth resuming. Deliberately does not clear an existing
            // state: replaying a played item would otherwise mark it unplayed at once.
            positionMs < MIN_SAVE_MS && !finished -> Unit
            finished -> markPlayed(itemId, durationMs)
            else -> dao.upsert(PlaybackProgressEntity(itemId.value, positionMs, durationMs, now()))
        }
    }

    override fun observeStates(): Flow<Map<MediaItemId, PlayState>> =
        dao.observeAll().map { rows -> rows.associate { MediaItemId(it.mediaItemId) to it.playState() } }

    override suspend fun setPlayed(itemId: MediaItemId, played: Boolean) {
        if (played) markPlayed(itemId, dao.get(itemId.value)?.durationMs) else dao.delete(itemId.value)
    }

    private suspend fun markPlayed(itemId: MediaItemId, durationMs: Long?) {
        val at = now()
        dao.upsert(
            PlaybackProgressEntity(
                mediaItemId = itemId.value,
                positionMs = durationMs ?: 0,
                durationMs = durationMs,
                updatedAtEpochMs = at,
                completedAtEpochMs = at,
            ),
        )
    }

    /**
     * How close to the end still counts as finished. A flat 15s is right for anything of
     * normal length but absurd for a Short: it marked a 30-second video played at the
     * halfway point, and a 60-second one at 75%. The tail is capped at a share of the
     * item instead, so "nearly finished" means the same thing at any duration.
     */
    private fun tailFor(durationMs: Long): Long =
        minOf(NEAR_END_MS, (durationMs * TAIL_FRACTION_PERCENT) / PERCENT)

    private fun PlaybackProgressEntity.playState(): PlayState =
        if (completedAtEpochMs != null) PlayState.Played else PlayState.InProgress(positionMs, durationMs)

    private companion object {
        const val MIN_SAVE_MS = 5_000L
        const val NEAR_END_MS = 15_000L
        const val TAIL_FRACTION_PERCENT = 10L
        const val PERCENT = 100L
    }
}
