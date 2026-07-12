package com.dewijones92.uniapp.data.search

import com.dewijones92.uniapp.common.HttpUrl

/** A non-blank search query. */
@JvmInline
public value class SearchQuery(public val value: String) {
    init {
        require(value.isNotBlank()) { "search query must not be blank" }
    }
}

/**
 * One search seam for every pillar: a source turns a query into [SearchHit]s.
 * Implementations exist for the podcast directory and for video search;
 * the UI renders all hits the same way and never knows which backend answered.
 */
public fun interface SearchSource {
    public suspend fun search(query: SearchQuery, limit: Int): SearchOutcome
}

public sealed interface SearchOutcome {
    public data class Success(val hits: List<SearchHit>) : SearchOutcome
    public data class Failure(val detail: String) : SearchOutcome
}

/** Something a search found; the variant determines its action. */
public sealed interface SearchHit {
    public val title: String
    public val subtitle: String?
    public val artworkUrl: HttpUrl?

    /** A subscribable podcast feed. */
    public data class Podcast(
        override val title: String,
        override val subtitle: String?,
        override val artworkUrl: HttpUrl?,
        val feedUrl: HttpUrl,
    ) : SearchHit

    /** A playable video (stream resolved on demand via the extraction engine). */
    public data class Video(
        override val title: String,
        override val subtitle: String?,
        override val artworkUrl: HttpUrl?,
        val watchUrl: HttpUrl,
        val durationSeconds: Long?,
    ) : SearchHit
}
