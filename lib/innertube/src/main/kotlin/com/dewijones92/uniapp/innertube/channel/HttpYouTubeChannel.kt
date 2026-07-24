package com.dewijones92.uniapp.innertube.channel

import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse
import com.dewijones92.uniapp.innertube.feeds.LockupParser

/**
 * [YouTubeChannel] over InnerTube's WEB `browse` (no auth — channel content is
 * public). Each tab is the same browse with a stable `params` token; the shared
 * [LockupParser] maps the tiles. One code path per tab, differing only by param
 * and which parser method runs.
 */
public class HttpYouTubeChannel(
    private val innerTube: InnerTubeClient,
) : YouTubeChannel {

    override suspend fun videos(channelId: String): ChannelVideos =
        when (val r = innerTube.browseWeb(channelId, VIDEOS_PARAMS)) {
            is InnerTubeResponse.Success -> ChannelVideos.Success(LockupParser.videos(r.body))
            InnerTubeResponse.Unauthorized -> ChannelVideos.Failure("Unauthorized")
            is InnerTubeResponse.Failure -> ChannelVideos.Failure(r.detail)
        }

    override suspend fun shorts(channelId: String): ChannelVideos =
        when (val r = innerTube.browseWeb(channelId, SHORTS_PARAMS)) {
            is InnerTubeResponse.Success -> ChannelVideos.Success(LockupParser.shorts(r.body))
            InnerTubeResponse.Unauthorized -> ChannelVideos.Failure("Unauthorized")
            is InnerTubeResponse.Failure -> ChannelVideos.Failure(r.detail)
        }

    override suspend fun playlists(channelId: String): ChannelPlaylists =
        when (val r = innerTube.browseWeb(channelId, PLAYLISTS_PARAMS)) {
            is InnerTubeResponse.Success -> ChannelPlaylists.Success(LockupParser.playlists(r.body))
            InnerTubeResponse.Unauthorized -> ChannelPlaylists.Failure("Unauthorized")
            is InnerTubeResponse.Failure -> ChannelPlaylists.Failure(r.detail)
        }

    private companion object {
        // Stable per-tab browse tokens (identical for every channel), verified live 2026-07-24.
        const val VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
        const val SHORTS_PARAMS = "EgZzaG9ydHPyBgUKA5oBAA=="
        const val PLAYLISTS_PARAMS = "EglwbGF5bGlzdHPyBgQKAkIA"
    }
}
