package com.dewijones92.totum.data.podcast

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.Subscription
import kotlinx.coroutines.flow.Flow

/** The app's single source of truth for podcasts. */
public interface PodcastRepository {

    /** All subscriptions, stable order (newest first). */
    public fun observeSubscriptions(): Flow<List<Subscription>>

    /** Episodes across all subscribed feeds, newest first. */
    public fun observeEpisodes(): Flow<List<MediaItem>>

    /** Fetches, parses, and stores [feedUrl]. Idempotent per URL. */
    public suspend fun subscribe(feedUrl: HttpUrl): SubscribeResult

    /** Removes the subscription and its episodes. */
    public suspend fun unsubscribe(id: SourceId)

    /**
     * Re-fetches every subscribed feed and upserts its episodes (pull-to-
     * refresh). Feeds that fail to fetch or parse are skipped, leaving their
     * stored episodes intact.
     */
    public suspend fun refresh()
}

/** Outcome of a subscribe attempt; expected failures are values. */
public sealed interface SubscribeResult {
    public data class Subscribed(val source: MediaSource.PodcastFeed) : SubscribeResult
    public data class AlreadySubscribed(val id: SourceId) : SubscribeResult

    public sealed interface Failure : SubscribeResult {
        public data class Network(val detail: String) : Failure
        public data class InvalidFeed(val detail: String) : Failure
    }
}
