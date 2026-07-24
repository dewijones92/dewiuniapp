package com.dewijones92.uniapp.innertube.channel.fake

import com.dewijones92.uniapp.innertube.channel.ChannelPlaylists
import com.dewijones92.uniapp.innertube.channel.ChannelVideos
import com.dewijones92.uniapp.innertube.channel.YouTubeChannel
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.playlists.Playlist

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
