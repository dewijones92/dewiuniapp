package com.dewijones92.uniapp.innertube.actions.fake

import com.dewijones92.uniapp.innertube.actions.ActionResult
import com.dewijones92.uniapp.innertube.actions.YouTubeActions

/** Records calls and returns a scripted result; for tests and previews. */
public class FakeYouTubeActions(
    public var result: ActionResult = ActionResult.Success,
) : YouTubeActions {

    public val subscribeCalls: MutableList<Pair<String, Boolean>> = mutableListOf()
    public val likeCalls: MutableList<Pair<String, Boolean>> = mutableListOf()
    public val commentCalls: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun setSubscribed(channelId: String, subscribed: Boolean): ActionResult {
        subscribeCalls += channelId to subscribed
        return result
    }

    override suspend fun setLiked(videoId: String, liked: Boolean): ActionResult {
        likeCalls += videoId to liked
        return result
    }

    override suspend fun postComment(videoId: String, text: String): ActionResult {
        commentCalls += videoId to text
        return result
    }
}
