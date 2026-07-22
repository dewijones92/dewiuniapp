package com.dewijones92.uniapp.innertube.comments

/**
 * Port: the top-level comments on a video. Reading comments needs no
 * sign-in (they are public), so this is independent of the account seam.
 */
public interface YouTubeComments {
    public suspend fun forVideo(videoId: String): CommentsResult

    /**
     * Loads a comment thread's replies given its [Comment.replyToken] (or a
     * "show more replies" token from a previous page). Rides the same
     * unauthenticated `next` continuation as the top-level comments.
     */
    public suspend fun repliesFor(token: String): RepliesResult
}

public sealed interface CommentsResult {
    public data class Success(val comments: List<Comment>) : CommentsResult

    /** Comments are turned off for this video. */
    public data object Disabled : CommentsResult

    public data class Failure(val detail: String) : CommentsResult
}

public sealed interface RepliesResult {
    /** [continuation] is a "show more replies" token when the thread has further pages. */
    public data class Success(val replies: List<Comment>, val continuation: String?) : RepliesResult

    public data class Failure(val detail: String) : RepliesResult
}
