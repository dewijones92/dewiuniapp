package com.dewijones92.uniapp.data.subscription

import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for subscriptions of one pillar (podcast feeds or video
 * channels). One implementation ([com.dewijones92.uniapp.database.RoomSubscriptionStore])
 * backs both, parameterised by source type — the seam is identical, only the
 * stored `MediaSource` variant differs.
 */
public interface SubscriptionStore {
    public fun observeSubscriptions(): Flow<List<Subscription>>
    public fun observeItems(): Flow<List<MediaItem>>
    public suspend fun contains(id: SourceId): Boolean
    public suspend fun saveSource(subscription: Subscription, items: List<MediaItem>)
    public suspend fun removeSource(id: SourceId)
}
