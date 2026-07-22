package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.actions.ActionResult
import com.dewijones92.uniapp.innertube.actions.YouTubeActions
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.subscriptions.SubscribedChannel
import com.dewijones92.uniapp.innertube.subscriptions.SubscriptionsResult
import com.dewijones92.uniapp.innertube.subscriptions.YouTubeSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The signed-in account's subscribed channels, read LIVE from YouTube — there
 * is no local mirror. This is the SmartTube model: sign in and your
 * subscriptions ARE your YouTube subscriptions; subscribing or unsubscribing
 * writes straight to YouTube and updates this list optimistically. Signed out,
 * the list is empty. Both the Videos tab's channel row and a channel page's
 * subscribe state read this one flow, so there is a single source of truth.
 */
class AccountSubscriptions(
    private val subscriptions: YouTubeSubscriptions,
    private val actions: YouTubeActions,
    private val account: YouTubeAccount,
    private val scope: CoroutineScope,
) {
    private val _channels = MutableStateFlow<List<MediaSource.VideoChannel>>(emptyList())
    val channels: StateFlow<List<MediaSource.VideoChannel>> = _channels.asStateFlow()

    private val _signedIn = MutableStateFlow(false)

    /** Whether the account is signed in, updated on each [refresh]; drives the feed UI. */
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** Reloads the live subscription list (on launch, on sign-in/out). Never blocks. */
    fun refresh() {
        scope.launch { reload() }
    }

    private suspend fun reload() {
        val signed = account.isSignedIn()
        _signedIn.value = signed
        if (!signed) {
            _channels.value = emptyList()
            return
        }
        when (val result = subscriptions.list()) {
            is SubscriptionsResult.Success -> _channels.value = result.channels.map { it.toSource() }
            SubscriptionsResult.SignedOut -> {
                _signedIn.value = false
                _channels.value = emptyList()
            }
            is SubscriptionsResult.Failure -> Unit // transient — keep what we have
        }
    }

    fun isSubscribed(id: SourceId): Boolean = _channels.value.any { it.id == id }

    /**
     * Subscribes to / unsubscribes from [source] on YouTube, updating the list
     * optimistically and reverting if the write fails. A channel URL without a
     * `/channel/<id>` can't be mirrored to the account, so it only updates the
     * in-memory list. Returns whether the write actually persisted to the account
     * (false on a revert or when there was no channel id to write).
     */
    suspend fun setSubscribed(source: MediaSource.VideoChannel, subscribed: Boolean): Boolean {
        val before = _channels.value
        _channels.update { list ->
            val without = list.filterNot { it.id == source.id }
            if (subscribed) without + source else without
        }
        val channelId = source.channelId() ?: return false
        val ok = actions.setSubscribed(channelId, subscribed) is ActionResult.Success
        if (!ok) _channels.value = before // revert on failure
        return ok
    }

    private fun SubscribedChannel.toSource() = MediaSource.VideoChannel(
        id = SourceId(channelUrl.value),
        title = title,
        channelUrl = channelUrl,
    )

    private fun MediaSource.VideoChannel.channelId(): String? =
        channelUrl.value.substringAfterLast("/channel/", "").ifBlank { null }
}
