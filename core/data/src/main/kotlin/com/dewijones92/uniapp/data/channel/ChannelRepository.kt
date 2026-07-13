package com.dewijones92.uniapp.data.channel

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow

/**
 * The app's source of truth for subscribed video channels. Mirrors
 * `PodcastRepository` — same shape, same shared `SubscriptionStore` — differing
 * only in that channels resolve through the extraction engine, not an RSS feed.
 */
public interface ChannelRepository {
    public fun observeSubscriptions(): Flow<List<Subscription>>
    public fun observeVideos(): Flow<List<MediaItem>>
    public suspend fun subscribe(channelUrl: HttpUrl): SubscribeChannelResult
    public suspend fun unsubscribe(id: SourceId)

    /**
     * Bulk-adds already-resolved channels (e.g. imported from a signed-in
     * YouTube account), skipping any already subscribed. Unlike [subscribe],
     * this does NOT re-extract each channel — the identity is already known —
     * so importing hundreds costs one store write each, not one network fetch.
     * Videos populate lazily on later refresh. Returns the count newly added.
     */
    public suspend fun importChannels(sources: List<MediaSource.VideoChannel>): Int

    /**
     * Fetches a channel's recent uploads for browsing — read-only, does not
     * subscribe or persist. Reuses the same extraction the subscribe path uses,
     * so the browse view and the subscribed feed show the same videos.
     */
    public suspend fun fetchChannelVideos(channelUrl: HttpUrl): ChannelVideosResult
}

/** Outcome of fetching a channel's videos for browsing; expected failures are values. */
public sealed interface ChannelVideosResult {
    public data class Success(val title: String, val videos: List<MediaItem>) : ChannelVideosResult

    public sealed interface Failure : ChannelVideosResult {
        public data class Network(val detail: String) : Failure
        public data class NotAChannel(val detail: String) : Failure
    }
}

/** Outcome of subscribing to a channel; expected failures are values. */
public sealed interface SubscribeChannelResult {
    public data class Subscribed(val source: MediaSource.VideoChannel) : SubscribeChannelResult
    public data class AlreadySubscribed(val id: SourceId) : SubscribeChannelResult

    public sealed interface Failure : SubscribeChannelResult {
        public data class Network(val detail: String) : Failure
        public data class NotAChannel(val detail: String) : Failure
    }
}
