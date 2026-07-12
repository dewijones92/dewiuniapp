package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow

/** Persistence port for podcasts; implemented by :core:database (Room). */
public interface PodcastStore {
    public fun observeSubscriptions(): Flow<List<Subscription>>
    public fun observeEpisodes(): Flow<List<MediaItem>>
    public suspend fun contains(id: SourceId): Boolean
    public suspend fun saveFeed(subscription: Subscription, episodes: List<MediaItem>)
    public suspend fun removeFeed(id: SourceId)
}
