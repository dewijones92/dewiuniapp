package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.innertube.actions.YouTubeActions

/**
 * Subscribing/unsubscribing to a video channel in one action that keeps both
 * sides in step: the signed-in YouTube account (so it syncs to YouTube's
 * servers) and the app's unified local subscription store (so the channel
 * appears — or disappears — alongside podcasts). Every place that toggles a
 * channel subscription goes through here, so the two-sided sync lives once.
 */
class ChannelSubscriptions(
    private val channels: ChannelRepository,
    private val actions: YouTubeActions,
) {
    suspend fun setSubscribed(source: MediaSource.VideoChannel, subscribed: Boolean) {
        // A YouTube channel URL carries the channel id after "/channel/"; only
        // then can we mirror the change to the account. Non-YouTube channels
        // (or handle URLs) just update locally.
        val channelId = source.channelUrl.value.substringAfterLast("/channel/", "").ifBlank { null }
        if (channelId != null) actions.setSubscribed(channelId, subscribed)
        // Re-add without re-extracting (identity already known); remove on unsubscribe.
        if (subscribed) channels.importChannels(listOf(source)) else channels.unsubscribe(source.id)
    }
}
