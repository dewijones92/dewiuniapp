package com.dewijones92.uniapp.innertube.actions

import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.AccessTokenResult
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse

/**
 * Write actions against InnerTube as the authenticated TV client. Endpoints
 * verified to work with just the target id (like/subscribe need no per-item
 * params token); comment posting uses a locally-built [CreateCommentParams].
 * JSON string values are escaped so comment text can't break the body.
 */
public class HttpYouTubeActions(
    private val account: YouTubeAccount,
    private val innerTube: InnerTubeClient,
    private val endpoints: ActionEndpoints = ActionEndpoints(),
) : YouTubeActions {

    /** The write endpoints; overridable so tests can point at a mock server. */
    public data class ActionEndpoints(
        val subscribe: String = InnerTubeClient.SUBSCRIBE_URL,
        val unsubscribe: String = InnerTubeClient.UNSUBSCRIBE_URL,
        val like: String = InnerTubeClient.LIKE_URL,
        val dislike: String = InnerTubeClient.DISLIKE_URL,
        val removeLike: String = InnerTubeClient.REMOVE_LIKE_URL,
        val createComment: String = InnerTubeClient.CREATE_COMMENT_URL,
        val editPlaylist: String = InnerTubeClient.EDIT_PLAYLIST_URL,
    )

    override suspend fun setSubscribed(channelId: String, subscribed: Boolean): ActionResult {
        val url = if (subscribed) endpoints.subscribe else endpoints.unsubscribe
        return act(url) { """"channelIds":["${escape(channelId)}"]""" }
    }

    override suspend fun setRating(videoId: String, rating: VideoRating): ActionResult {
        val url = when (rating) {
            VideoRating.LIKE -> endpoints.like
            VideoRating.DISLIKE -> endpoints.dislike
            VideoRating.NONE -> endpoints.removeLike
        }
        return act(url) { """"target":{"videoId":"${escape(videoId)}"}""" }
    }

    override suspend fun setSavedToWatchLater(videoId: String, saved: Boolean): ActionResult {
        val action = if (saved) {
            """{"action":"ACTION_ADD_VIDEO","addedVideoId":"${escape(videoId)}"}"""
        } else {
            """{"action":"ACTION_REMOVE_VIDEO","removedVideoId":"${escape(videoId)}"}"""
        }
        return act(endpoints.editPlaylist) { """"playlistId":"WL","actions":[$action]""" }
    }

    override suspend fun postComment(videoId: String, text: String): ActionResult =
        act(endpoints.createComment) {
            """"createCommentParams":"${CreateCommentParams.forVideo(videoId)}","commentText":"${escape(text)}""""
        }

    private suspend fun act(url: String, fields: () -> String): ActionResult {
        val token = when (val result = account.accessToken()) {
            is AccessTokenResult.Available -> result.token
            AccessTokenResult.SignedOut -> return ActionResult.SignedOut
            is AccessTokenResult.Failure -> return ActionResult.Failure(result.detail)
        }
        return perform(url, fields(), token)
    }

    private suspend fun perform(url: String, fields: String, token: AccessToken): ActionResult =
        when (val response = innerTube.action(url, fields, token)) {
            is InnerTubeResponse.Success -> ActionResult.Success
            InnerTubeResponse.Unauthorized -> ActionResult.SignedOut
            is InnerTubeResponse.Failure -> ActionResult.Failure(response.detail)
        }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
}
