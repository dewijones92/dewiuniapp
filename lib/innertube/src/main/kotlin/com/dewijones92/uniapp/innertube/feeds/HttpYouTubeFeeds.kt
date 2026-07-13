package com.dewijones92.uniapp.innertube.feeds

import com.dewijones92.uniapp.innertube.auth.AccessTokenResult
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse

/**
 * Fetches account video feeds by browsing the matching InnerTube id with a
 * live token from [YouTubeAccount], parsing via [VideoTileParser]. One code
 * path per feed; the feed differs only by its [AccountFeed] browse id.
 */
public class HttpYouTubeFeeds(
    private val account: YouTubeAccount,
    private val innerTube: InnerTubeClient,
) : YouTubeFeeds {

    override suspend fun recommended(): FeedResult = fetch(AccountFeed.RECOMMENDED)
    override suspend fun subscriptionsFeed(): FeedResult = fetch(AccountFeed.SUBSCRIPTIONS)
    override suspend fun watchLater(): FeedResult = fetch(AccountFeed.WATCH_LATER)
    override suspend fun history(): FeedResult = fetch(AccountFeed.HISTORY)

    private suspend fun fetch(feed: AccountFeed): FeedResult {
        val token = when (val result = account.accessToken()) {
            is AccessTokenResult.Available -> result.token
            AccessTokenResult.SignedOut -> return FeedResult.SignedOut
            is AccessTokenResult.Failure -> return FeedResult.Failure(result.detail)
        }
        return when (val browsed = innerTube.browse(feed.browseId, token)) {
            is InnerTubeResponse.Success -> VideoTileParser.parse(browsed.body)
            InnerTubeResponse.Unauthorized -> FeedResult.SignedOut
            is InnerTubeResponse.Failure -> FeedResult.Failure(browsed.detail)
        }
    }
}
