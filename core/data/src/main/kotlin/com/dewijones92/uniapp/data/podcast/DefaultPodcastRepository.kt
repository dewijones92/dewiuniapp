package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.rss.ParsedEpisode
import com.dewijones92.uniapp.data.rss.ParsedFeed
import com.dewijones92.uniapp.data.rss.RssParseResult
import com.dewijones92.uniapp.data.rss.RssParser
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import java.time.Clock

public class DefaultPodcastRepository(
    private val fetcher: FeedFetcher,
    private val store: PodcastStore,
    private val parser: RssParser = RssParser(),
    private val clock: Clock = Clock.systemUTC(),
) : PodcastRepository {

    override fun observeSubscriptions(): Flow<List<Subscription>> = store.observeSubscriptions()

    override fun observeEpisodes(): Flow<List<MediaItem>> = store.observeEpisodes()

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
        store.saveFeed(
            subscription = Subscription(source = source, subscribedAt = clock.instant()),
            episodes = parsed.episodes.mapIndexed { index, episode -> episode.toMediaItem(id, feedUrl, index) },
        )
        return SubscribeResult.Subscribed(source)
    }

    override suspend fun unsubscribe(id: SourceId) {
        store.removeFeed(id)
    }

    private fun ParsedFeed.toMediaSource(id: SourceId, feedUrl: HttpUrl) = MediaSource.PodcastFeed(
        id = id,
        title = title,
        feedUrl = feedUrl,
        websiteUrl = websiteUrl?.let(HttpUrl::parse),
    )

    private fun ParsedEpisode.toMediaItem(sourceId: SourceId, feedUrl: HttpUrl, index: Int) = MediaItem(
        // Stable per feed: guid, else enclosure, else position — in that order of trust.
        id = MediaItemId(guid ?: enclosureUrl ?: "${feedUrl.value}#$index"),
        sourceId = sourceId,
        title = title,
        publishedAt = publishedAt,
        duration = duration,
        description = description,
        thumbnailUrl = imageUrl?.let(HttpUrl::parse),
        mediaUrl = enclosureUrl?.let(HttpUrl::parse),
    )
}
