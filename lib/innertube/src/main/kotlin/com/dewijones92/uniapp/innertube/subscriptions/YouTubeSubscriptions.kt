package com.dewijones92.uniapp.innertube.subscriptions

/**
 * Port: the signed-in user's subscribed channels, from YouTube's account
 * feeds. The pillar-agnostic subscription store consumes the result via a
 * small adapter — this only knows YouTube.
 */
public interface YouTubeSubscriptions {
    public suspend fun list(): SubscriptionsResult
}

public sealed interface SubscriptionsResult {
    public data class Success(val channels: List<SubscribedChannel>) : SubscriptionsResult

    /** No live session — the user must sign in (again). */
    public data object SignedOut : SubscriptionsResult

    public data class Failure(val detail: String) : SubscriptionsResult
}
