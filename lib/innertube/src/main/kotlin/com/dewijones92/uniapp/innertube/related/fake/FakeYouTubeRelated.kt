package com.dewijones92.uniapp.innertube.related.fake

import com.dewijones92.uniapp.innertube.related.RelatedResult
import com.dewijones92.uniapp.innertube.related.YouTubeRelated

/** Scriptable [YouTubeRelated] for tests and previews; no network. */
public class FakeYouTubeRelated(
    public var result: RelatedResult = RelatedResult.Success(emptyList()),
) : YouTubeRelated {
    override suspend fun relatedTo(videoId: String): RelatedResult = result
}
