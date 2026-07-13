package com.dewijones92.uniapp.innertube.comments

/**
 * Port: the top-level comments on a video. Reading comments needs no
 * sign-in (they are public), so this is independent of the account seam.
 */
public interface YouTubeComments {
    public suspend fun forVideo(videoId: String): CommentsResult
}

public sealed interface CommentsResult {
    public data class Success(val comments: List<Comment>) : CommentsResult

    /** Comments are turned off for this video. */
    public data object Disabled : CommentsResult

    public data class Failure(val detail: String) : CommentsResult
}
