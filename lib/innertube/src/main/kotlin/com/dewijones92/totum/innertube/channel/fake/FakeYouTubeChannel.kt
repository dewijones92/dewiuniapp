package com.dewijones92.totum.innertube.channel.fake

import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.innertube.channel.ChannelPlaylists
import com.dewijones92.totum.innertube.channel.ChannelVideos
import com.dewijones92.totum.innertube.channel.YouTubeChannel
import com.dewijones92.totum.innertube.feeds.FeedVideo
import com.dewijones92.totum.innertube.playlists.Playlist

/**
 * In-memory [YouTubeChannel] for tests and previews.
 *
 * [videoPages] scripts later pages by the token that asks for them, so a test can drive a
 * real "load more" sequence; [requested] records every call so it can assert the
 * continuation was actually followed.
 */
public class FakeYouTubeChannel(
    private val videos: List<FeedVideo> = emptyList(),
    private val shorts: List<FeedVideo> = emptyList(),
    private val playlists: List<Playlist> = emptyList(),
    private val videosNext: PageToken? = null,
    private val videoPages: Map<String, Page<FeedVideo>> = emptyMap(),
) : YouTubeChannel {

    public val requested: MutableList<PageToken?> = mutableListOf()

    override suspend fun videos(channelId: String, after: PageToken?): ChannelVideos {
        requested += after
        val page = after?.let { videoPages[it.value] } ?: Page(videos, videosNext)
        return ChannelVideos.Success(page)
    }

    override suspend fun shorts(channelId: String, after: PageToken?): ChannelVideos =
        ChannelVideos.Success(Page.last(shorts))

    override suspend fun playlists(channelId: String, after: PageToken?): ChannelPlaylists =
        ChannelPlaylists.Success(Page.last(playlists))
}
