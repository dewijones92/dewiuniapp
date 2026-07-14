package com.dewijones92.uniapp.innertube.actions

/**
 * Port for authenticated write actions on the signed-in account. Each mutates
 * real YouTube state, so callers should confirm intent; results are values.
 */
public interface YouTubeActions {
    public suspend fun setSubscribed(channelId: String, subscribed: Boolean): ActionResult
    public suspend fun setRating(videoId: String, rating: VideoRating): ActionResult
    public suspend fun postComment(videoId: String, text: String): ActionResult
}

/** A video's rating on the signed-in account; like and dislike are mutually exclusive. */
public enum class VideoRating { NONE, LIKE, DISLIKE }

public sealed interface ActionResult {
    public data object Success : ActionResult

    /** No live session — the user must sign in (again). */
    public data object SignedOut : ActionResult

    public data class Failure(val detail: String) : ActionResult
}
