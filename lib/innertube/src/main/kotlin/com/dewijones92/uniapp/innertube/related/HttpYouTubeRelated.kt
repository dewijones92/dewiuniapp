package com.dewijones92.uniapp.innertube.related

import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse

/**
 * Reads a video's related / "up next" list from one unauthenticated WEB `next`
 * call — the same watch-page request that backs comments — and parses the
 * video lockups out of it.
 */
public class HttpYouTubeRelated(
    private val innerTube: InnerTubeClient,
) : YouTubeRelated {

    override suspend fun relatedTo(videoId: String): RelatedResult =
        when (val result = innerTube.next(videoId)) {
            is InnerTubeResponse.Success -> RelatedVideosParser.parse(result.body)
            InnerTubeResponse.Unauthorized -> RelatedResult.Failure("Unauthorized")
            is InnerTubeResponse.Failure -> RelatedResult.Failure(result.detail)
        }
}
