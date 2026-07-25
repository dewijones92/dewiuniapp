package com.dewijones92.totum.innertube.channel.fake

import com.dewijones92.totum.innertube.channel.ChannelPlaylists
import com.dewijones92.totum.innertube.channel.ChannelVideos
import com.dewijones92.totum.innertube.channel.YouTubeChannel
import com.dewijones92.totum.innertube.feeds.FeedVideo
import com.dewijones92.totum.innertube.playlists.Playlist

/** In-memory [YouTubeChannel] for tests and previews. */
public class FakeYouTubeChannel(
    private val videos: List<FeedVideo> = emptyList(),
    private val shorts: List<FeedVideo> = emptyList(),
    private val playlists: List<Playlist> = emptyList(),
) : YouTubeChannel {
    override suspend fun videos(channelId: String): ChannelVideos = ChannelVideos.Success(videos)
    override suspend fun shorts(channelId: String): ChannelVideos = ChannelVideos.Success(shorts)
    override suspend fun playlists(channelId: String): ChannelPlaylists = ChannelPlaylists.Success(playlists)
}
