package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.PodcastStore
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Room-backed [PodcastStore]; the only place entities and domain types meet. */
public class RoomPodcastStore(private val dao: PodcastDao) : PodcastStore {

    override fun observeSubscriptions(): Flow<List<Subscription>> =
        dao.observeFeeds().map { feeds -> feeds.map { it.toSubscription() } }

    override fun observeEpisodes(): Flow<List<MediaItem>> =
        dao.observeEpisodes().map { episodes -> episodes.map { it.toMediaItem() } }

    override suspend fun contains(id: SourceId): Boolean = dao.countFeeds(id.value) > 0

    override suspend fun saveFeed(subscription: Subscription, episodes: List<MediaItem>) {
        val source = subscription.source
        check(source is MediaSource.PodcastFeed) { "PodcastStore only stores podcast feeds" }
        dao.upsertFeedWithEpisodes(
            feed = FeedEntity(
                id = source.id.value,
                title = source.title,
                feedUrl = source.feedUrl.value,
                websiteUrl = source.websiteUrl?.value,
                subscribedAtEpochMs = subscription.subscribedAt.toEpochMilli(),
            ),
            episodes = episodes.map { it.toEntity() },
        )
    }

    override suspend fun removeFeed(id: SourceId) {
        dao.deleteFeed(id.value)
    }

    private fun FeedEntity.toSubscription() = Subscription(
        source = MediaSource.PodcastFeed(
            id = SourceId(id),
            title = title,
            feedUrl = HttpUrl.of(feedUrl),
            websiteUrl = websiteUrl?.let(HttpUrl::parse),
        ),
        subscribedAt = Instant.ofEpochMilli(subscribedAtEpochMs),
    )

    private fun EpisodeEntity.toMediaItem() = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId(feedId),
        title = title,
        publishedAt = publishedAtEpochMs?.let(Instant::ofEpochMilli),
        duration = durationSeconds?.seconds,
        author = author,
        description = description,
        thumbnailUrl = thumbnailUrl?.let(HttpUrl::parse),
        mediaUrl = mediaUrl?.let(HttpUrl::parse),
    )

    private fun MediaItem.toEntity() = EpisodeEntity(
        id = id.value,
        feedId = sourceId.value,
        title = title,
        author = author,
        publishedAtEpochMs = publishedAt?.toEpochMilli(),
        durationSeconds = duration?.inWholeSeconds,
        description = description,
        thumbnailUrl = thumbnailUrl?.value,
        mediaUrl = mediaUrl?.value,
    )
}
