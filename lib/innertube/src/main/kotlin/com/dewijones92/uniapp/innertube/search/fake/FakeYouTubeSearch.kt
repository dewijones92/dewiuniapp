package com.dewijones92.uniapp.innertube.search.fake

import com.dewijones92.uniapp.innertube.search.SearchVideosResult
import com.dewijones92.uniapp.innertube.search.SearchedVideo
import com.dewijones92.uniapp.innertube.search.YouTubeSearch

/** In-memory [YouTubeSearch] for tests and previews. */
public class FakeYouTubeSearch(
    private var result: SearchVideosResult = SearchVideosResult.Success(emptyList()),
) : YouTubeSearch {

    /** Makes every query answer with [videos]. */
    public fun registerVideos(videos: List<SearchedVideo>) {
        result = SearchVideosResult.Success(videos)
    }

    public fun registerFailure(detail: String) {
        result = SearchVideosResult.Failure(detail)
    }

    override suspend fun searchVideos(query: String, limit: Int): SearchVideosResult =
        when (val current = result) {
            is SearchVideosResult.Success -> SearchVideosResult.Success(current.videos.take(limit))
            is SearchVideosResult.Failure -> current
        }
}
