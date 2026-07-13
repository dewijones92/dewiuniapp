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
