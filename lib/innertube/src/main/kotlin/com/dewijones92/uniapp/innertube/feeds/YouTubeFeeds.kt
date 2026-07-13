package com.dewijones92.uniapp.innertube.feeds

/**
 * Port: the signed-in user's video feeds. Four feeds, one shape — each is a
 * list of [FeedVideo]. The app renders them through its one media row.
 */
public interface YouTubeFeeds {
    public suspend fun recommended(): FeedResult
    public suspend fun subscriptionsFeed(): FeedResult
    public suspend fun watchLater(): FeedResult
    public suspend fun history(): FeedResult
}

/** The feeds, named so callers don't hard-code InnerTube browse ids. */
public enum class AccountFeed(internal val browseId: String) {
    RECOMMENDED("FEwhat_to_watch"),
    SUBSCRIPTIONS("FEsubscriptions"),
    WATCH_LATER("VLWL"),
    HISTORY("FEhistory"),
}

public sealed interface FeedResult {
    public data class Success(val videos: List<FeedVideo>) : FeedResult
    public data object SignedOut : FeedResult
    public data class Failure(val detail: String) : FeedResult
}
