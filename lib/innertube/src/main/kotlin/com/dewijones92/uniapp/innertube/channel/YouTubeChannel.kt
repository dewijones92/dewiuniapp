package com.dewijones92.uniapp.innertube.channel

import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.playlists.Playlist

/**
 * Browses a YouTube channel's public tabs — Videos, Shorts, Playlists — via
 * InnerTube. Public content, so no sign-in is needed. One seam feeds the tabbed
 * channel page; videos carry their published date ("2 days ago") for free.
 */
public interface YouTubeChannel {

    /** The channel's uploads (newest first), with published dates. */
    public suspend fun videos(channelId: String): ChannelVideos

    /** The channel's Shorts. */
    public suspend fun shorts(channelId: String): ChannelVideos

    /** The channel's playlists. */
    public suspend fun playlists(channelId: String): ChannelPlaylists
}

/** Videos (or Shorts) of a channel tab; expected failures are values. */
public sealed interface ChannelVideos {
    public data class Success(val videos: List<FeedVideo>) : ChannelVideos
    public data class Failure(val detail: String) : ChannelVideos
}

/** A channel's playlists; expected failures are values. */
public sealed interface ChannelPlaylists {
    public data class Success(val playlists: List<Playlist>) : ChannelPlaylists
    public data class Failure(val detail: String) : ChannelPlaylists
}
