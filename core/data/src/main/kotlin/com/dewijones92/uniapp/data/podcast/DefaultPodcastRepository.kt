package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.net.FetchResult
import com.dewijones92.uniapp.data.net.HttpTextFetcher
import com.dewijones92.uniapp.data.rss.ParsedEpisode
import com.dewijones92.uniapp.data.rss.ParsedFeed
import com.dewijones92.uniapp.data.rss.PodcastChaptersJson
import com.dewijones92.uniapp.data.rss.RssParseResult
import com.dewijones92.uniapp.data.rss.RssParser
import com.dewijones92.uniapp.data.subscription.SubscriptionStore
import com.dewijones92.uniapp.domain.Chapter
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
                episode.toMediaItem(id, feedUrl, index, parsed.title, resolveChapters(episode, index))
            },
        )
        return SubscribeResult.Subscribed(source)
    }

    override suspend fun unsubscribe(id: SourceId) {
        store.removeSource(id)
    }

    override suspend fun refresh() {
        store.observeSubscriptions().first().forEach { refreshFeed(it) }
    }

    /** Re-fetches one feed's episodes; a fetch/parse failure leaves the stored episodes intact. */
    private suspend fun refreshFeed(sub: Subscription) {
        val source = sub.source as? MediaSource.PodcastFeed ?: return
        val body = (fetcher.fetch(source.feedUrl) as? FetchResult.Success)?.body ?: return
        val parsed = (parser.parse(body) as? RssParseResult.Success)?.feed ?: return
        store.saveSource(
            // Keep the original subscribedAt so refreshing doesn't reorder feeds.
            subscription = Subscription(source = source, subscribedAt = sub.subscribedAt),
            items = parsed.episodes.mapIndexed { index, episode ->
                episode.toMediaItem(source.id, source.feedUrl, index, parsed.title, resolveChapters(episode, index))
            },
        )
    }

    private fun ParsedFeed.toMediaSource(id: SourceId, feedUrl: HttpUrl) = MediaSource.PodcastFeed(
        id = id,
        title = title,
        feedUrl = feedUrl,
        websiteUrl = websiteUrl?.let(HttpUrl::parse),
    )

    /**
     * Chapters for an episode: inline Podlove chapters if present, else the
     * Podcasting 2.0 remote chapters JSON — fetched only for the newest episodes
     * (a feed can link one per episode) and fail-open, like SponsorBlock.
     */
    private suspend fun resolveChapters(episode: ParsedEpisode, index: Int): List<Chapter> {
        if (episode.chapters.isNotEmpty()) return episode.chapters
        val url = episode.chaptersUrl
            ?.takeIf { index < REMOTE_CHAPTERS_LIMIT }
            ?.let(HttpUrl::parse) ?: return emptyList()
        val body = (fetcher.fetch(url) as? FetchResult.Success)?.body ?: return emptyList()
        return PodcastChaptersJson.parse(body)
    }

    private fun ParsedEpisode.toMediaItem(
        sourceId: SourceId,
        feedUrl: HttpUrl,
        index: Int,
        feedTitle: String,
        chapters: List<Chapter>,
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
        chapters = chapters,
    )

    private companion object {
        /** Cap on remote-chapter fetches per feed refresh, so a fully-chaptered feed can't fan out. */
        const val REMOTE_CHAPTERS_LIMIT = 30
    }
}
