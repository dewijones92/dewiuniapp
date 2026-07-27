package com.dewijones92.totum.innertube.search

import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken
import com.dewijones92.totum.innertube.browse.Continuations
import com.dewijones92.totum.innertube.browse.InnerTubeClient
import com.dewijones92.totum.innertube.browse.InnerTubeResponse
import com.dewijones92.totum.innertube.browse.SearchTarget
import kotlinx.serialization.json.Json

/** [YouTubeSearch] over InnerTube's public search endpoint (WEB client, no auth). */
public class HttpYouTubeSearch(private val client: InnerTubeClient) : YouTubeSearch {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun searchVideos(query: String, limit: Int, after: PageToken?): SearchVideosResult {
        val target = after?.let { SearchTarget.Continuation(it.value) } ?: SearchTarget.Query(query)
        return when (val response = client.search(target)) {
            is InnerTubeResponse.Success -> SearchVideosResult.Success(response.body.toPage(limit))
            // Search needs no token, so a 401/403 here is YouTube refusing the
            // request rather than a sign-in problem — report it as a failure.
            InnerTubeResponse.Unauthorized -> SearchVideosResult.Failure("rejected")
            is InnerTubeResponse.Failure -> SearchVideosResult.Failure(response.detail)
        }
    }

    /**
     * The continuation is dropped when the page came back empty: YouTube will keep
     * handing out a token forever, and following one that yields nothing is an endless
     * scroll that never adds a row.
     */
    private fun String.toPage(limit: Int): Page<SearchedVideo> {
        val videos = SearchResultsParser.videos(this).take(limit)
        if (videos.isEmpty()) return Page.last(videos)
        val root = runCatching { json.parseToJsonElement(this) }.getOrNull() ?: return Page.last(videos)
        return Page(videos, Continuations.find(root))
    }
}
