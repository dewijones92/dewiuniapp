package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
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
