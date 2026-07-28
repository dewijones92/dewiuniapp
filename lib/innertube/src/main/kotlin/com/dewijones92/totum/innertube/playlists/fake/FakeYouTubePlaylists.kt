package com.dewijones92.totum.innertube.playlists.fake

import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.innertube.playlists.PlaylistVideosResult
import com.dewijones92.totum.innertube.playlists.PlaylistsResult
import com.dewijones92.totum.innertube.playlists.YouTubePlaylists

/** Scriptable [YouTubePlaylists] for tests and previews; no network. */
public class FakeYouTubePlaylists(
    public var playlists: PlaylistsResult = PlaylistsResult.Success(emptyList()),
    public var videos: PlaylistVideosResult = PlaylistVideosResult.Success(Page.empty()),
) : YouTubePlaylists {
    override suspend fun myPlaylists(): PlaylistsResult = playlists

    /** Every requested continuation, so a test can assert the token was threaded through. */
    public val requestedTokens: MutableList<PageToken?> = mutableListOf()

    override suspend fun videosIn(browseId: String, after: PageToken?): PlaylistVideosResult {
        requestedTokens.add(after)
        return videos
    }
}
