package com.dewijones92.totum.data.search

import com.dewijones92.totum.innertube.search.SearchVideosResult
import com.dewijones92.totum.innertube.search.YouTubeSearch

/**
 * Video search over InnerTube. Preferred over [YtDlpVideoSearchSource] because
 * the WEB search response carries each result's **upload date** (yt-dlp's flat
 * `ytsearch` doesn't) and needs no Python interpreter to answer.
 */
public class InnerTubeVideoSearchSource(private val search: YouTubeSearch) : SearchSource {

    override suspend fun search(query: SearchQuery, limit: Int): SearchOutcome =
        when (val result = search.searchVideos(query.value, limit)) {
            is SearchVideosResult.Failure -> SearchOutcome.Failure(result.detail)
            is SearchVideosResult.Success -> SearchOutcome.Success(
                result.videos.map { video ->
                    SearchHit.Video(
                        title = video.title,
                        subtitle = video.author,
                        artworkUrl = video.thumbnailUrl,
                        watchUrl = video.watchUrl,
                        durationSeconds = video.durationSeconds,
                        publishedText = video.publishedText,
                    )
                },
            )
        }
}
