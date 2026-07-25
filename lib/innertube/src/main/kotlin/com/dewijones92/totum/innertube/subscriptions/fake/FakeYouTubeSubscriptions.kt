package com.dewijones92.totum.innertube.subscriptions.fake

import com.dewijones92.totum.innertube.subscriptions.SubscriptionsResult
import com.dewijones92.totum.innertube.subscriptions.YouTubeSubscriptions

/** Scriptable [YouTubeSubscriptions] for tests and previews; no network. */
public class FakeYouTubeSubscriptions(
    public var result: SubscriptionsResult = SubscriptionsResult.Success(emptyList()),
) : YouTubeSubscriptions {
    override suspend fun list(): SubscriptionsResult = result
}
