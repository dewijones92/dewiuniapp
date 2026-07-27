package com.dewijones92.totum.innertube.search.fake

import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.innertube.search.SearchVideosResult
import com.dewijones92.totum.innertube.search.SearchedVideo
import com.dewijones92.totum.innertube.search.YouTubeSearch

/** In-memory [YouTubeSearch] for tests and previews. */
public class FakeYouTubeSearch(
    private var result: SearchVideosResult = SearchVideosResult.Success(Page.empty()),
) : YouTubeSearch {

    /** Makes every query answer with [videos], as a single final page. */
    public fun registerVideos(videos: List<SearchedVideo>) {
        result = SearchVideosResult.Success(Page.last(videos))
    }

    /** Pages handed out in order, so a test can drive "load more" to exhaustion. */
    public fun registerPages(vararg pages: Page<SearchedVideo>) {
        queued.clear()
        queued.addAll(pages)
    }

    public fun registerFailure(detail: String) {
        result = SearchVideosResult.Failure(detail)
    }

    private val queued = mutableListOf<Page<SearchedVideo>>()

    /** Every continuation asked for, in order — lets a test assert the token was used. */
    public val requestedTokens: MutableList<PageToken?> = mutableListOf()

    override suspend fun searchVideos(query: String, limit: Int, after: PageToken?): SearchVideosResult {
        requestedTokens.add(after)
        queued.removeFirstOrNull()?.let { return SearchVideosResult.Success(it) }
        return when (val current = result) {
            is SearchVideosResult.Success ->
                SearchVideosResult.Success(Page(current.page.items.take(limit), current.page.next))
            is SearchVideosResult.Failure -> current
        }
    }
}
