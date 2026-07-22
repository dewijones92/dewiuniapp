package com.dewijones92.uniapp.innertube.playlists.fake

import com.dewijones92.uniapp.innertube.playlists.PlaylistVideosResult
import com.dewijones92.uniapp.innertube.playlists.PlaylistsResult
import com.dewijones92.uniapp.innertube.playlists.YouTubePlaylists

/** Scriptable [YouTubePlaylists] for tests and previews; no network. */
public class FakeYouTubePlaylists(
    public var playlists: PlaylistsResult = PlaylistsResult.Success(emptyList()),
    public var videos: PlaylistVideosResult = PlaylistVideosResult.Success(emptyList()),
) : YouTubePlaylists {
    override suspend fun myPlaylists(): PlaylistsResult = playlists

    override suspend fun videosIn(browseId: String): PlaylistVideosResult = videos
}
