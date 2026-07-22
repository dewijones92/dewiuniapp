package com.dewijones92.uniapp.innertube.feeds

import com.dewijones92.uniapp.common.HttpUrl

/**
 * A video in an account feed (home, subscriptions, watch later, history).
 * [watchUrl] is the stable handle resolved to a stream on play; the same
 * shape backs every feed, so one parser and one row render them all.
 */
public data class FeedVideo(
    val videoId: String,
    val title: String,
    val author: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: HttpUrl?,
    val watchUrl: HttpUrl,
    /** Normal video, live stream or Short — lets a unified feed tag each item. */
    val kind: Kind = Kind.VIDEO,
) {
    public enum class Kind { VIDEO, LIVE, SHORT }

    public companion object {
        public fun watchUrlFor(videoId: String): HttpUrl? =
            HttpUrl.parse("https://www.youtube.com/watch?v=$videoId")
    }
}
