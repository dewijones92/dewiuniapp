package com.dewijones92.uniapp.data.podcast.fake

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.SubscribeResult
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory [PodcastRepository] for tests and Compose previews. Subscribing
 * to any valid URL succeeds with a canned feed named after the URL's host.
 */
public class FakePodcastRepository(
    initialSubscriptions: List<Subscription> = emptyList(),
    initialEpisodes: List<MediaItem> = emptyList(),
) : PodcastRepository {

    private val subscriptions = MutableStateFlow(initialSubscriptions)
    private val episodes = MutableStateFlow(initialEpisodes)

    override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptions

    override fun observeEpisodes(): Flow<List<MediaItem>> = episodes

    override suspend fun subscribe(feedUrl: HttpUrl): SubscribeResult {
        val id = SourceId(feedUrl.value)
        if (subscriptions.value.any { it.source.id == id }) return SubscribeResult.AlreadySubscribed(id)

        val source = MediaSource.PodcastFeed(id = id, title = feedUrl.value, feedUrl = feedUrl)
        subscriptions.update { it + Subscription(source, subscribedAt = Instant.EPOCH) }
        episodes.update { it + sampleEpisode(id) }
        return SubscribeResult.Subscribed(source)
    }

    override suspend fun unsubscribe(id: SourceId) {
        subscriptions.update { list -> list.filterNot { it.source.id == id } }
        episodes.update { list -> list.filterNot { it.sourceId == id } }
    }

    public companion object {
        /** A ready-made episode for previews and tests. */
        public fun sampleEpisode(
            sourceId: SourceId,
            title: String = "Sample episode",
        ): MediaItem = MediaItem(
            id = MediaItemId("${sourceId.value}/sample"),
            sourceId = sourceId,
            title = title,
            publishedAt = Instant.parse("2026-07-01T09:00:00Z"),
            duration = 42.minutes,
            author = "Sample author",
            description = "A sample episode.",
        )
    }
}
