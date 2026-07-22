package com.dewijones92.uniapp.innertube.playlists

import com.dewijones92.uniapp.innertube.feeds.FeedVideo

/**
 * Port: the signed-in account's playlists and their contents. Both read live
 * from the account (never copied), like the other account feeds.
 */
public interface YouTubePlaylists {
    public suspend fun myPlaylists(): PlaylistsResult

    /** Videos in a playlist, given its [Playlist.browseId]. */
    public suspend fun videosIn(browseId: String): PlaylistVideosResult
}

public sealed interface PlaylistsResult {
    public data class Success(val playlists: List<Playlist>) : PlaylistsResult
    public data object SignedOut : PlaylistsResult
    public data class Failure(val detail: String) : PlaylistsResult
}

public sealed interface PlaylistVideosResult {
    public data class Success(val videos: List<FeedVideo>) : PlaylistVideosResult
    public data object SignedOut : PlaylistVideosResult
    public data class Failure(val detail: String) : PlaylistVideosResult
}
