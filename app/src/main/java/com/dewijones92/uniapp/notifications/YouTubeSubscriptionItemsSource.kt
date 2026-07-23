package com.dewijones92.uniapp.notifications

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.content.SourceUpdate
import com.dewijones92.uniapp.data.content.SubscriptionItemsSource
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.feeds.FeedResult
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds
import com.dewijones92.uniapp.ui.common.toMediaItem

/**
 * The YouTube pillar's contribution to the refresh. The TV-OAuth token can't
 * reach a real per-channel notification feed, so — like the in-app bell — this
 * reads the aggregate subscriptions feed and reports it as one synthetic
 * "YouTube subscriptions" source. New-video detection is left to
 * [com.dewijones92.uniapp.data.content.ContentRefresher]. Signed out ⇒ nothing.
 */
public class YouTubeSubscriptionItemsSource(
    private val feeds: YouTubeFeeds,
) : SubscriptionItemsSource {

    override suspend fun currentItems(): List<SourceUpdate> {
        val videos = (feeds.subscriptionsFeed() as? FeedResult.Success)?.videos ?: return emptyList()
        if (videos.isEmpty()) return emptyList()
        return listOf(SourceUpdate(SUBSCRIPTIONS_SOURCE, videos.map { it.toMediaItem(SUBSCRIPTIONS_SOURCE.id) }))
    }

    public companion object {
        /** The single synthetic source the aggregate YouTube subscriptions feed reports under. */
        public val SUBSCRIPTIONS_SOURCE: MediaSource.VideoChannel = MediaSource.VideoChannel(
            id = SourceId("yt:subscriptions"),
            title = "YouTube subscriptions",
            channelUrl = HttpUrl.of("https://www.youtube.com/feed/subscriptions"),
        )
    }
}
