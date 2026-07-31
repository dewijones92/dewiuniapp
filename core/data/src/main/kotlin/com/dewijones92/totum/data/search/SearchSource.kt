package com.dewijones92.totum.data.search

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.PageToken

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
    /**
     * [after] continues a previous page; null starts a fresh search.
     *
     * A directory that answers in one shot (iTunes) returns [Page.last], and that is a
     * complete answer rather than a special case — which is what lets one infinite
     * scroll serve both pillars.
     *
     * No default for [after]: this stays a `fun interface` so a test can supply a source
     * as a lambda, and Kotlin forbids defaults on the abstract method of one.
     */
    public suspend fun search(query: SearchQuery, limit: Int, after: PageToken?): SearchOutcome
}

public sealed interface SearchOutcome {
    public data class Success(val page: Page<SearchHit>) : SearchOutcome
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
        /** How the source renders the upload date ("1 year ago"); null when unknown. */
        val publishedText: String? = null,
        /** How the source renders the view count ("1.2M views"); null when unknown. */
        val viewsText: String? = null,
        /** Behind a channel membership — it will not play or download without one. */
        val membersOnly: Boolean = false,
        /**
         * The uploader's channel page, when the source named it. Becomes
         * [com.dewijones92.totum.domain.MediaItem.sourceUrl], so "go to channel" from a search
         * result is instant instead of costing a full extraction to discover.
         */
        val channelUrl: HttpUrl? = null,
    ) : SearchHit
}
