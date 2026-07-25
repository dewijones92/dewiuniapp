package com.dewijones92.uniapp.innertube.feeds.fake

import com.dewijones92.uniapp.common.PageToken
import com.dewijones92.uniapp.innertube.feeds.AccountFeed
import com.dewijones92.uniapp.innertube.feeds.FeedResult
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds

/**
 * Scriptable [YouTubeFeeds] for tests and previews; no network.
 *
 * [pages] scripts later pages by the token that asks for them, so a test can drive a
 * real "load more" sequence rather than only a first page. Every request is recorded in
 * [requested], which is how a test asserts that a continuation was actually followed.
 */
public class FakeYouTubeFeeds(
    public var results: MutableMap<AccountFeed, FeedResult> = mutableMapOf(),
    public var default: FeedResult = FeedResult.Success(emptyList()),
    public var pages: MutableMap<String, FeedResult> = mutableMapOf(),
) : YouTubeFeeds {

    public val requested: MutableList<Pair<AccountFeed, PageToken?>> = mutableListOf()

    override suspend fun recommended(after: PageToken?): FeedResult = fetch(AccountFeed.RECOMMENDED, after)
    override suspend fun subscriptionsFeed(after: PageToken?): FeedResult = fetch(AccountFeed.SUBSCRIPTIONS, after)
    override suspend fun watchLater(after: PageToken?): FeedResult = fetch(AccountFeed.WATCH_LATER, after)
    override suspend fun history(after: PageToken?): FeedResult = fetch(AccountFeed.HISTORY, after)

    private fun fetch(feed: AccountFeed, after: PageToken?): FeedResult {
        requested += feed to after
        return after?.let { pages[it.value] } ?: results[feed] ?: default
    }
}
