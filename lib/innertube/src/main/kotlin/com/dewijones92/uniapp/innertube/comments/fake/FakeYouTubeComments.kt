package com.dewijones92.uniapp.innertube.comments.fake

import com.dewijones92.uniapp.innertube.comments.CommentsResult
import com.dewijones92.uniapp.innertube.comments.RepliesResult
import com.dewijones92.uniapp.innertube.comments.YouTubeComments

/** Scriptable [YouTubeComments] for tests and previews; no network. */
public class FakeYouTubeComments(
    public var result: CommentsResult = CommentsResult.Success(emptyList()),
    public var replies: RepliesResult = RepliesResult.Success(emptyList(), null),
) : YouTubeComments {
    override suspend fun forVideo(videoId: String): CommentsResult = result

    override suspend fun repliesFor(token: String): RepliesResult = replies
}
