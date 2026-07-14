package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.subscription.ReconcileResult
import com.dewijones92.uniapp.data.subscription.SubscriptionStore
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Room-backed [SubscriptionStore]. One class serves both pillars: [sourceType]
 * selects whether this instance stores/reads podcast feeds or video channels,
 * using the same tables. The only place entities and domain types meet.
 */
public class RoomSubscriptionStore(
    private val dao: PodcastDao,
    private val sourceType: SourceType,
) : SubscriptionStore {

    public enum class SourceType(internal val key: String) { PODCAST("podcast"), CHANNEL("channel") }

    private companion object {
        const val ORIGIN_MANUAL = "manual"
        const val ORIGIN_IMPORTED = "youtube_import"
    }

    override fun observeSubscriptions(): Flow<List<Subscription>> =
        dao.observeFeeds(sourceType.key).map { feeds -> feeds.map { it.toSubscription() } }

    override fun observeItems(): Flow<List<MediaItem>> =
        dao.observeEpisodes(sourceType.key).map { items -> items.map { it.toMediaItem() } }

    override suspend fun contains(id: SourceId): Boolean = dao.countFeeds(id.value) > 0

    override suspend fun saveSource(subscription: Subscription, items: List<MediaItem>) {
        dao.upsertFeedWithEpisodes(
            feed = subscription.source.toEntity(subscription.subscribedAt),
            episodes = items.map { it.toEntity() },
        )
    }

    override suspend fun removeSource(id: SourceId) {
        dao.deleteFeed(id.value)
    }

    override suspend fun reconcileImported(sources: List<MediaSource>, subscribedAt: Instant): ReconcileResult {
        // Offer every source as imported; the DAO's insert ignores ids already
        // stored, so an existing manual row keeps its origin (and its protection
        // from pruning) while genuinely new ones land as imported.
        val counts = dao.reconcileImported(
            sourceType = sourceType.key,
            origin = ORIGIN_IMPORTED,
            feeds = sources.map { it.toEntity(subscribedAt, origin = ORIGIN_IMPORTED) },
            keepIds = sources.map { it.id.value },
        )
        return ReconcileResult(added = counts.added, removed = counts.removed)
    }

    private fun MediaSource.toEntity(subscribedAt: Instant, origin: String = ORIGIN_MANUAL): FeedEntity = when (this) {
        is MediaSource.PodcastFeed -> FeedEntity(
            id = id.value,
            sourceType = SourceType.PODCAST.key,
            title = title,
            feedUrl = feedUrl.value,
            websiteUrl = websiteUrl?.value,
            subscribedAtEpochMs = subscribedAt.toEpochMilli(),
            origin = origin,
        )
        is MediaSource.VideoChannel -> FeedEntity(
            id = id.value,
            sourceType = SourceType.CHANNEL.key,
            title = title,
            feedUrl = channelUrl.value,
            websiteUrl = null,
            subscribedAtEpochMs = subscribedAt.toEpochMilli(),
            origin = origin,
        )
    }

    private fun FeedEntity.toSubscription(): Subscription {
        val source = when (sourceType) {
            SourceType.CHANNEL.key -> MediaSource.VideoChannel(
                id = SourceId(id),
                title = title,
                channelUrl = HttpUrl.of(feedUrl),
            )
            else -> MediaSource.PodcastFeed(
                id = SourceId(id),
                title = title,
                feedUrl = HttpUrl.of(feedUrl),
                websiteUrl = websiteUrl?.let(HttpUrl::parse),
            )
        }
        return Subscription(source = source, subscribedAt = Instant.ofEpochMilli(subscribedAtEpochMs))
    }

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
