package com.dewijones92.totum.innertube.channel

import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.innertube.browse.BrowseTarget
import com.dewijones92.totum.innertube.browse.InnerTubeClient
import com.dewijones92.totum.innertube.browse.InnerTubeResponse
import com.dewijones92.totum.innertube.feeds.LockupParser

/**
 * [YouTubeChannel] over InnerTube's WEB `browse` (no auth — channel content is public).
 *
 * Each tab is the same browse with a stable `params` token, and a later page is the same
 * browse with a continuation instead. That sameness is expressed as one [tab] call rather
 * than three copies of the request-and-map dance, so paging was added in one place.
 */
public class HttpYouTubeChannel(
    private val innerTube: InnerTubeClient,
) : YouTubeChannel {

    override suspend fun videos(channelId: String, after: PageToken?): ChannelVideos =
        when (val r = tab(channelId, VIDEOS_PARAMS, after, LockupParser::videos)) {
            is TabResult.Ok -> ChannelVideos.Success(r.page)
            is TabResult.Err -> ChannelVideos.Failure(r.detail)
        }

    override suspend fun shorts(channelId: String, after: PageToken?): ChannelVideos =
        when (val r = tab(channelId, SHORTS_PARAMS, after, LockupParser::shorts)) {
            is TabResult.Ok -> ChannelVideos.Success(r.page)
            is TabResult.Err -> ChannelVideos.Failure(r.detail)
        }

    override suspend fun playlists(channelId: String, after: PageToken?): ChannelPlaylists =
        when (val r = tab(channelId, PLAYLISTS_PARAMS, after, LockupParser::playlists)) {
            is TabResult.Ok -> ChannelPlaylists.Success(r.page)
            is TabResult.Err -> ChannelPlaylists.Failure(r.detail)
        }

    /**
     * Browses one tab, first page or later. A continuation already encodes which tab it
     * continues, so the params are only sent for the first page.
     */
    private suspend fun <T> tab(
        channelId: String,
        params: String,
        after: PageToken?,
        parse: (String) -> Page<T>,
    ): TabResult<T> {
        val target = after?.let { BrowseTarget.Continuation(it.value) }
            ?: BrowseTarget.Id(channelId, params)
        return when (val response = innerTube.browseWeb(target)) {
            is InnerTubeResponse.Success -> TabResult.Ok(parse(response.body))
            InnerTubeResponse.Unauthorized -> TabResult.Err("Unauthorized")
            is InnerTubeResponse.Failure -> TabResult.Err(response.detail)
        }
    }

    private sealed interface TabResult<out T> {
        data class Ok<T>(val page: Page<T>) : TabResult<T>
        data class Err(val detail: String) : TabResult<Nothing>
    }

    private companion object {
        // Stable per-tab browse tokens (identical for every channel), verified live 2026-07-24.
        const val VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
        const val SHORTS_PARAMS = "EgZzaG9ydHPyBgUKA5oBAA=="
        const val PLAYLISTS_PARAMS = "EglwbGF5bGlzdHPyBgQKAkIA"
    }
}
