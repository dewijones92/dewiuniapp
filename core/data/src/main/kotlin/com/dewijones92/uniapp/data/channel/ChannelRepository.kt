package com.dewijones92.uniapp.data.channel

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem

/**
 * Read-only access to a video channel: its recent uploads, for browsing a
 * channel page or resolving a channel URL to subscribe. Channel subscriptions
 * themselves are NOT stored here — they live on the signed-in YouTube account
 * (see `AccountSubscriptions`), read live, SmartTube-style. Podcasts, which
 * have no cloud account, keep their own local `PodcastRepository`.
 */
public interface ChannelRepository {
    /**
     * Fetches a channel's recent uploads for browsing — read-only, persists
     * nothing. [ChannelVideosResult.Success.channelId] is the resolved `UC…`
     * id, so a URL a user pastes can be turned into a live YouTube subscribe.
     */
    public suspend fun fetchChannelVideos(channelUrl: HttpUrl): ChannelVideosResult
}

/** Outcome of fetching a channel's videos for browsing; expected failures are values. */
public sealed interface ChannelVideosResult {
    public data class Success(
        val channelId: String,
        val title: String,
        val videos: List<MediaItem>,
    ) : ChannelVideosResult

    public sealed interface Failure : ChannelVideosResult {
        public data class Network(val detail: String) : Failure
        public data class NotAChannel(val detail: String) : Failure
    }
}
