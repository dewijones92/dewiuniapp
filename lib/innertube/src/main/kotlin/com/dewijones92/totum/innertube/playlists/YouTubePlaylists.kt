package com.dewijones92.totum.innertube.playlists

import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.innertube.feeds.FeedVideo

/**
 * Port: the signed-in account's playlists and their contents. Both read live
 * from the account (never copied), like the other account feeds.
 */
public interface YouTubePlaylists {
    public suspend fun myPlaylists(): PlaylistsResult

    /** Videos in a playlist, given its [Playlist.browseId]. */
    /**
     * A playlist's videos, one page at a time. [after] continues from a previous page's
     * token; null starts at the beginning.
     */
    public suspend fun videosIn(browseId: String, after: PageToken? = null): PlaylistVideosResult
}

public sealed interface PlaylistsResult {
    public data class Success(val playlists: List<Playlist>) : PlaylistsResult
    public data object SignedOut : PlaylistsResult
    public data class Failure(val detail: String) : PlaylistsResult
}

public sealed interface PlaylistVideosResult {
    public data class Success(val page: Page<FeedVideo>) : PlaylistVideosResult
    public data object SignedOut : PlaylistVideosResult
    public data class Failure(val detail: String) : PlaylistVideosResult
}
