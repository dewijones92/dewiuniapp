package com.dewijones92.uniapp.data.channel

import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.subscriptions.SubscriptionsResult
import com.dewijones92.uniapp.innertube.subscriptions.YouTubeSubscriptions

/**
 * Imports the signed-in YouTube account's subscriptions into the app's own
 * subscription model. The bridge between the YouTube-specific account seam
 * ([YouTubeSubscriptions], in :lib:innertube) and the pillar-agnostic
 * [ChannelRepository] — each fetched channel becomes a `VideoChannel` written
 * through the same store a manually-added channel uses.
 */
public class SubscriptionImporter(
    private val subscriptions: YouTubeSubscriptions,
    private val channels: ChannelRepository,
) {

    public suspend fun import(): ImportResult = when (val result = subscriptions.list()) {
        is SubscriptionsResult.Success -> {
            val sources = result.channels.map {
                MediaSource.VideoChannel(
                    id = SourceId(it.channelUrl.value),
                    title = it.title,
                    channelUrl = it.channelUrl,
                )
            }
            val outcome = channels.syncImportedChannels(sources)
            ImportResult.Imported(added = outcome.added, removed = outcome.removed, total = sources.size)
        }
        SubscriptionsResult.SignedOut -> ImportResult.SignedOut
        is SubscriptionsResult.Failure -> ImportResult.Failure(result.detail)
    }
}

/** Outcome of a subscription import; expected failures are values. */
public sealed interface ImportResult {
    /** [added] newly pulled in, [removed] pruned (left the account's subs), of [total] fetched. */
    public data class Imported(val added: Int, val removed: Int, val total: Int) : ImportResult
    public data object SignedOut : ImportResult
    public data class Failure(val detail: String) : ImportResult
}
