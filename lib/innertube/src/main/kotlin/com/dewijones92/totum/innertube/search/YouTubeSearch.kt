package com.dewijones92.totum.innertube.search

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken

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
    /** How YouTube renders the view count ("1.2M views"); null if the result omits it. */
    public val viewsText: String? = null,
    /** Behind a channel membership — it will not play or download without one. */
    public val membersOnly: Boolean = false,
)

public sealed interface SearchVideosResult {
    public data class Success(public val page: Page<SearchedVideo>) : SearchVideosResult
    public data class Failure(public val detail: String) : SearchVideosResult
}

/**
 * Public video search over InnerTube (no sign-in). Exists alongside yt-dlp's
 * `ytsearch` because the WEB search response carries each result's **upload
 * date**, which yt-dlp's flat search results don't.
 */
public interface YouTubeSearch {
    /** [after] continues a previous page; null starts a fresh search for [query]. */
    public suspend fun searchVideos(query: String, limit: Int, after: PageToken? = null): SearchVideosResult
}
