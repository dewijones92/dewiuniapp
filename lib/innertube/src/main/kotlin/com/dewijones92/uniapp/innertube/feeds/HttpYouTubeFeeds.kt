package com.dewijones92.uniapp.innertube.feeds

import com.dewijones92.uniapp.common.PageToken
import com.dewijones92.uniapp.innertube.auth.AccessTokenResult
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.browse.BrowseTarget
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse

/**
 * Fetches account video feeds by browsing the matching InnerTube id with a live token
 * from [YouTubeAccount], parsing via [VideoTileParser]. One code path per feed; the feed
 * differs only by its [AccountFeed] browse id, and a later page only by carrying a
 * continuation token instead.
 */
public class HttpYouTubeFeeds(
    private val account: YouTubeAccount,
    private val innerTube: InnerTubeClient,
) : YouTubeFeeds {

    override suspend fun recommended(after: PageToken?): FeedResult = fetch(AccountFeed.RECOMMENDED, after)
    override suspend fun subscriptionsFeed(after: PageToken?): FeedResult = fetch(AccountFeed.SUBSCRIPTIONS, after)
    override suspend fun watchLater(after: PageToken?): FeedResult = fetch(AccountFeed.WATCH_LATER, after)
    override suspend fun history(after: PageToken?): FeedResult = fetch(AccountFeed.HISTORY, after)

    private suspend fun fetch(feed: AccountFeed, after: PageToken?): FeedResult {
        val token = when (val result = account.accessToken()) {
            is AccessTokenResult.Available -> result.token
            AccessTokenResult.SignedOut -> return FeedResult.SignedOut
            is AccessTokenResult.Failure -> return FeedResult.Failure(result.detail)
        }
        val target = after?.let { BrowseTarget.Continuation(it.value) } ?: BrowseTarget.Id(feed.browseId)
        return when (val browsed = innerTube.browse(target, token)) {
            is InnerTubeResponse.Success -> VideoTileParser.parse(browsed.body)
            InnerTubeResponse.Unauthorized -> FeedResult.SignedOut
            is InnerTubeResponse.Failure -> FeedResult.Failure(browsed.detail)
        }
    }
}
