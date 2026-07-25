package com.dewijones92.totum.innertube.feeds

import com.dewijones92.totum.common.PageToken

/**
 * Port: the signed-in user's video feeds. Four feeds, one shape — each is a page of
 * [FeedVideo]. The app renders them through its one media row.
 *
 * Every feed is paged: YouTube returns roughly one screenful per request, so without
 * following continuations a feed silently ends at page one. Passing [after] asks for the
 * page following that token; passing null asks for the first.
 */
public interface YouTubeFeeds {
    public suspend fun recommended(after: PageToken? = null): FeedResult
    public suspend fun subscriptionsFeed(after: PageToken? = null): FeedResult
    public suspend fun watchLater(after: PageToken? = null): FeedResult
    public suspend fun history(after: PageToken? = null): FeedResult
}

/** The feeds, named so callers don't hard-code InnerTube browse ids. */
public enum class AccountFeed(internal val browseId: String) {
    RECOMMENDED("FEwhat_to_watch"),
    SUBSCRIPTIONS("FEsubscriptions"),
    WATCH_LATER("VLWL"),
    HISTORY("FEhistory"),
}

public sealed interface FeedResult {
    /** [next] is null when this is the last page — see `Page` for why that is ordinary. */
    public data class Success(val videos: List<FeedVideo>, val next: PageToken? = null) : FeedResult
    public data object SignedOut : FeedResult
    public data class Failure(val detail: String) : FeedResult
}
