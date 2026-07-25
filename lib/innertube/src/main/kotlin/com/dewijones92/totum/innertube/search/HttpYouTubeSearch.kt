package com.dewijones92.totum.innertube.search

import com.dewijones92.totum.innertube.browse.InnerTubeClient
import com.dewijones92.totum.innertube.browse.InnerTubeResponse

/** [YouTubeSearch] over InnerTube's public search endpoint (WEB client, no auth). */
public class HttpYouTubeSearch(private val client: InnerTubeClient) : YouTubeSearch {

    override suspend fun searchVideos(query: String, limit: Int): SearchVideosResult =
        when (val response = client.search(query)) {
            is InnerTubeResponse.Success ->
                SearchVideosResult.Success(SearchResultsParser.videos(response.body).take(limit))
            // Search needs no token, so a 401/403 here is YouTube refusing the
            // request rather than a sign-in problem — report it as a failure.
            InnerTubeResponse.Unauthorized -> SearchVideosResult.Failure("rejected")
            is InnerTubeResponse.Failure -> SearchVideosResult.Failure(response.detail)
        }
}
