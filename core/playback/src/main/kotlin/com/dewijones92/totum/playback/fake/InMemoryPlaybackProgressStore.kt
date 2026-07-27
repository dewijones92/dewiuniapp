package com.dewijones92.totum.playback.fake

import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayState
import com.dewijones92.totum.playback.PlaybackProgressStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [PlaybackProgressStore] for tests and previews.
 *
 * Records exactly what it is told, with none of the Room store's rules (ignore trivial
 * positions, treat near-the-end as finished). Anything asserting those rules belongs in
 * the instrumented test of the real store; anything asserting a *caller's* behaviour
 * wants this, where what goes in is what comes out.
 */
public class InMemoryPlaybackProgressStore : PlaybackProgressStore {

    private val states = MutableStateFlow<Map<MediaItemId, PlayState>>(emptyMap())

    override suspend fun resumePositionMs(itemId: MediaItemId): Long? =
        (states.value[itemId] as? PlayState.InProgress)?.positionMs

    override suspend fun save(itemId: MediaItemId, positionMs: Long, durationMs: Long?) {
        states.update { it + (itemId to PlayState.InProgress(positionMs, durationMs)) }
    }

    override fun observeStates(): Flow<Map<MediaItemId, PlayState>> = states

    override suspend fun setPlayed(itemId: MediaItemId, played: Boolean) {
        states.update { if (played) it + (itemId to PlayState.Played) else it - itemId }
    }
}
