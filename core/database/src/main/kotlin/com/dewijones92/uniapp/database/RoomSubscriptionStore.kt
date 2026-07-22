package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.subscription.SubscriptionStore
import com.dewijones92.uniapp.domain.Chapter
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
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

    private fun MediaSource.toEntity(subscribedAt: Instant): FeedEntity = when (this) {
        is MediaSource.PodcastFeed -> FeedEntity(
            id = id.value,
            sourceType = SourceType.PODCAST.key,
            title = title,
            feedUrl = feedUrl.value,
            websiteUrl = websiteUrl?.value,
            subscribedAtEpochMs = subscribedAt.toEpochMilli(),
        )
        is MediaSource.VideoChannel -> FeedEntity(
            id = id.value,
            sourceType = SourceType.CHANNEL.key,
            title = title,
            feedUrl = channelUrl.value,
            websiteUrl = null,
            subscribedAtEpochMs = subscribedAt.toEpochMilli(),
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
        chapters = chapters.decodeChapters(),
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
        chapters = chapters.encodeChapters(),
    )
}

/** Chapters ↔ the compact JSON stored in one episode column (`[{"s":startMs,"t":title}]`). */
private fun List<Chapter>.encodeChapters(): String? {
    if (isEmpty()) return null
    return buildJsonArray {
        forEach { chapter ->
            add(
                buildJsonObject {
                    put("s", chapter.start.inWholeMilliseconds)
                    put("t", chapter.title)
                },
            )
        }
    }.toString()
}

private fun String?.decodeChapters(): List<Chapter> {
    if (this == null) return emptyList()
    return runCatching {
        Json.parseToJsonElement(this).jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val start = obj["s"]?.jsonPrimitive?.long ?: return@mapNotNull null
            val title = obj["t"]?.jsonPrimitive?.content?.ifBlank { null } ?: return@mapNotNull null
            Chapter(start.milliseconds, title)
        }
    }.getOrDefault(emptyList())
}
