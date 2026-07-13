package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.net.FetchResult
import com.dewijones92.uniapp.data.net.HttpTextFetcher
import com.dewijones92.uniapp.data.rss.ParsedEpisode
import com.dewijones92.uniapp.data.rss.ParsedFeed
import com.dewijones92.uniapp.data.rss.RssParseResult
import com.dewijones92.uniapp.data.rss.RssParser
import com.dewijones92.uniapp.data.subscription.SubscriptionStore
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import java.time.Clock

public class DefaultPodcastRepository(
    private val fetcher: HttpTextFetcher,
    private val store: SubscriptionStore,
    private val parser: RssParser = RssParser(),
    private val clock: Clock = Clock.systemUTC(),
) : PodcastRepository {

    override fun observeSubscriptions(): Flow<List<Subscription>> = store.observeSubscriptions()

    override fun observeEpisodes(): Flow<List<MediaItem>> = store.observeItems()

    override suspend fun subscribe(feedUrl: HttpUrl): SubscribeResult {
        val id = SourceId(feedUrl.value)
        if (store.contains(id)) return SubscribeResult.AlreadySubscribed(id)

        val body = when (val fetched = fetcher.fetch(feedUrl)) {
            is FetchResult.Success -> fetched.body
            is FetchResult.Failure -> return SubscribeResult.Failure.Network(fetched.detail)
        }

        val parsed = when (val result = parser.parse(body)) {
            is RssParseResult.Success -> result.feed
            is RssParseResult.Failure -> return SubscribeResult.Failure.InvalidFeed(result.detail)
        }

        val source = parsed.toMediaSource(id, feedUrl)
        store.saveSource(
            subscription = Subscription(source = source, subscribedAt = clock.instant()),
            items = parsed.episodes.mapIndexed { index, episode ->
                episode.toMediaItem(id, feedUrl, index, feedTitle = parsed.title)
            },
        )
        return SubscribeResult.Subscribed(source)
    }

    override suspend fun unsubscribe(id: SourceId) {
        store.removeSource(id)
    }

    private fun ParsedFeed.toMediaSource(id: SourceId, feedUrl: HttpUrl) = MediaSource.PodcastFeed(
        id = id,
        title = title,
        feedUrl = feedUrl,
        websiteUrl = websiteUrl?.let(HttpUrl::parse),
    )

    private fun ParsedEpisode.toMediaItem(
        sourceId: SourceId,
        feedUrl: HttpUrl,
        index: Int,
        feedTitle: String,
    ) = MediaItem(
        // Stable per feed: guid, else enclosure, else position — in that order of trust.
        id = MediaItemId(guid ?: enclosureUrl ?: "${feedUrl.value}#$index"),
        sourceId = sourceId,
        title = title,
        publishedAt = publishedAt,
        duration = duration,
        author = author ?: feedTitle,
        description = description,
        thumbnailUrl = imageUrl?.let(HttpUrl::parse),
        mediaUrl = enclosureUrl?.let(HttpUrl::parse),
    )
}
