package com.dewijones92.totum.data.subscription.fake

import com.dewijones92.totum.data.subscription.SubscriptionStore
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [SubscriptionStore] for tests and previews. */
public class InMemorySubscriptionStore : SubscriptionStore {

    private val sources = MutableStateFlow<Map<SourceId, Pair<Subscription, List<MediaItem>>>>(emptyMap())

    override fun observeSubscriptions(): Flow<List<Subscription>> =
        sources.map { saved -> saved.values.map { it.first } }

    override fun observeItems(): Flow<List<MediaItem>> =
        sources.map { saved -> saved.values.flatMap { it.second } }

    override suspend fun contains(id: SourceId): Boolean = sources.value.containsKey(id)

    override suspend fun saveSource(subscription: Subscription, items: List<MediaItem>) {
        sources.update { it + (subscription.source.id to (subscription to items)) }
    }

    override suspend fun removeSource(id: SourceId) {
        sources.update { it - id }
    }
}
