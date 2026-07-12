package com.dewijones92.uniapp.ytdlp

import com.dewijones92.uniapp.common.HttpUrl

/** Result of a video search; expected failures are values. */
public sealed interface VideoSearchResult {
    public data class Success(val entries: List<VideoSearchEntry>) : VideoSearchResult
    public data class Failure(val detail: String) : VideoSearchResult
}

/** One search hit — enough to render a result and extract it on demand. */
public data class VideoSearchEntry(
    val id: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long?,
    val watchUrl: HttpUrl,
    val thumbnailUrl: String?,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
    }
}
