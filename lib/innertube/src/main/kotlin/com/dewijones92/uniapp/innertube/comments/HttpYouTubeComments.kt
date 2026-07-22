package com.dewijones92.uniapp.innertube.comments

import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse

/**
 * Reads a video's comments over two unauthenticated WEB `next` calls: fetch
 * the watch page to get the comment section's continuation token, then follow
 * it and parse. No token means comments are turned off for that video.
 */
public class HttpYouTubeComments(
    private val innerTube: InnerTubeClient,
) : YouTubeComments {

    override suspend fun forVideo(videoId: String): CommentsResult {
        val watchPage = when (val result = innerTube.next(videoId)) {
            is InnerTubeResponse.Success -> result.body
            InnerTubeResponse.Unauthorized -> return CommentsResult.Failure("Unauthorized")
            is InnerTubeResponse.Failure -> return CommentsResult.Failure(result.detail)
        }
        val continuation = CommentsResponseParser.findCommentsContinuation(watchPage)
            ?: return CommentsResult.Disabled
        return when (val result = innerTube.nextContinuation(continuation)) {
            is InnerTubeResponse.Success -> CommentsResponseParser.parseComments(result.body)
            InnerTubeResponse.Unauthorized -> CommentsResult.Failure("Unauthorized")
            is InnerTubeResponse.Failure -> CommentsResult.Failure(result.detail)
        }
    }

    override suspend fun repliesFor(token: String): RepliesResult =
        when (val result = innerTube.nextContinuation(token)) {
            is InnerTubeResponse.Success -> CommentsResponseParser.parseReplies(result.body)
            InnerTubeResponse.Unauthorized -> RepliesResult.Failure("Unauthorized")
            is InnerTubeResponse.Failure -> RepliesResult.Failure(result.detail)
        }
}
