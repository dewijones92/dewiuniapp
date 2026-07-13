package com.dewijones92.uniapp.innertube.feeds.fake

import com.dewijones92.uniapp.innertube.feeds.AccountFeed
import com.dewijones92.uniapp.innertube.feeds.FeedResult
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds

/** Scriptable [YouTubeFeeds] for tests and previews; no network. */
public class FakeYouTubeFeeds(
    public var results: MutableMap<AccountFeed, FeedResult> = mutableMapOf(),
    public var default: FeedResult = FeedResult.Success(emptyList()),
) : YouTubeFeeds {
    override suspend fun recommended(): FeedResult = results[AccountFeed.RECOMMENDED] ?: default
    override suspend fun subscriptionsFeed(): FeedResult = results[AccountFeed.SUBSCRIPTIONS] ?: default
    override suspend fun watchLater(): FeedResult = results[AccountFeed.WATCH_LATER] ?: default
    override suspend fun history(): FeedResult = results[AccountFeed.HISTORY] ?: default
}
