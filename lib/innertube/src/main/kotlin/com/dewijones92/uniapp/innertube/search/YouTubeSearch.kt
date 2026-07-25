package com.dewijones92.uniapp.innertube.search

import com.dewijones92.uniapp.common.HttpUrl

/** One video a search returned. */
public data class SearchedVideo(
    public val videoId: String,
    public val title: String,
    public val author: String?,
    /** How YouTube renders the upload date ("1 year ago"); null if absent. */
    public val publishedText: String?,
    public val durationSeconds: Long?,
    public val thumbnailUrl: HttpUrl?,
    public val watchUrl: HttpUrl,
)

public sealed interface SearchVideosResult {
    public data class Success(public val videos: List<SearchedVideo>) : SearchVideosResult
    public data class Failure(public val detail: String) : SearchVideosResult
}

/**
 * Public video search over InnerTube (no sign-in). Exists alongside yt-dlp's
 * `ytsearch` because the WEB search response carries each result's **upload
 * date**, which yt-dlp's flat search results don't.
 */
public interface YouTubeSearch {
    public suspend fun searchVideos(query: String, limit: Int): SearchVideosResult
}
