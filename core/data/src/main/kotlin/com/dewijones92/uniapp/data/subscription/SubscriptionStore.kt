package com.dewijones92.uniapp.data.subscription

import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for a pillar's subscriptions. Used by the podcast pillar
 * (RSS feeds have no cloud account, so they persist locally); YouTube channel
 * subscriptions are NOT stored here — they live on the account, read live.
 */
public interface SubscriptionStore {
    public fun observeSubscriptions(): Flow<List<Subscription>>
    public fun observeItems(): Flow<List<MediaItem>>
    public suspend fun contains(id: SourceId): Boolean
    public suspend fun saveSource(subscription: Subscription, items: List<MediaItem>)
    public suspend fun removeSource(id: SourceId)
}
