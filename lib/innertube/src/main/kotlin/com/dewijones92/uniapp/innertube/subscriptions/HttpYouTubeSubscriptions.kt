package com.dewijones92.uniapp.innertube.subscriptions

import com.dewijones92.uniapp.innertube.auth.AccessTokenResult
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse

/**
 * Fetches subscribed channels by browsing the account's `FEchannels` feed
 * with a live token from [YouTubeAccount] (refreshed transparently), then
 * parsing via [SubscriptionsResponseParser].
 */
public class HttpYouTubeSubscriptions(
    private val account: YouTubeAccount,
    private val innerTube: InnerTubeClient,
) : YouTubeSubscriptions {

    override suspend fun list(): SubscriptionsResult {
        val token = when (val result = account.accessToken()) {
            is AccessTokenResult.Available -> result.token
            AccessTokenResult.SignedOut -> return SubscriptionsResult.SignedOut
            is AccessTokenResult.Failure -> return SubscriptionsResult.Failure(result.detail)
        }
        return when (val browsed = innerTube.browse(SUBSCRIPTIONS_BROWSE_ID, token)) {
            is InnerTubeResponse.Success -> SubscriptionsResponseParser.parse(browsed.body)
            InnerTubeResponse.Unauthorized -> SubscriptionsResult.SignedOut
            is InnerTubeResponse.Failure -> SubscriptionsResult.Failure(browsed.detail)
        }
    }

    private companion object {
        const val SUBSCRIPTIONS_BROWSE_ID = "FEchannels"
    }
}
