package com.dewijones92.totum.innertube.browse

import com.dewijones92.totum.innertube.auth.AccessToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Minimal client for YouTube's private InnerTube API. Two shapes:
 *
 * - [browse] — authenticated, impersonating the living-room TV app (the same
 *   client our device-code OAuth authenticates as; the WEB client rejects a TV
 *   token, verified against YouTube). Serves every account feed, first page or
 *   later, depending on its [BrowseTarget].
 * - [next] — the watch-page endpoint used unauthenticated with the WEB client
 *   for public data like comments (no token needed, and the WEB comment format
 *   is far simpler than the TV one).
 *
 * Both go through one [execute] so the HTTP + error mapping lives in one place.
 */
// The count is InnerTube's endpoint surface — browse, next, search, player, the write
// actions — plus three small body builders. Splitting it would scatter the one place that
// knows how to talk to InnerTube, which is the point of the class.
@Suppress("TooManyFunctions")
public class InnerTubeClient(
    private val client: OkHttpClient,
    private val browseUrl: String = BROWSE_URL,
    private val nextUrl: String = NEXT_URL,
    private val searchUrl: String = SEARCH_URL,
    private val playerUrl: String = PLAYER_URL,
    private val tvClientVersion: String = TV_CLIENT_VERSION,
    private val webClientVersion: String = WEB_CLIENT_VERSION,
    private val androidClientVersion: String = ANDROID_CLIENT_VERSION,
) {

    public suspend fun browse(target: BrowseTarget, accessToken: AccessToken): InnerTubeResponse =
        execute(browseUrl, tvContext(target.fields()), accessToken)

    /**
     * A video's streaming data, as the ANDROID client.
     *
     * That client specifically, because it is the only one YouTube still serves playable
     * streams to for restricted content — measured across all twelve of yt-dlp's clients on
     * 2026-07-30. It is also the response that carries `serverAbrStreamingUrl`, which is how
     * the formats WITHOUT a plain URL are fetched (see docs/todos/sabr-streaming.md).
     *
     * Unauthenticated: the TV client refuses with "Sign in to confirm you're not a bot"
     * unless it can present a full session, which a bearer token alone is not.
     */
    public suspend fun player(videoId: String): InnerTubeResponse =
        execute(playerUrl, androidContext(videoId), bearer = null)

    /** Watch-page data for a video (WEB client, no auth). */
    public suspend fun next(videoId: String): InnerTubeResponse =
        execute(nextUrl, webContext(""" "videoId":"$videoId" """), bearer = null)

    /** Browses public content (WEB client, no auth) — e.g. a channel's tabs. */
    public suspend fun browseWeb(target: BrowseTarget): InnerTubeResponse =
        execute(browseUrl, webContext(target.fields()), bearer = null)

    /**
     * Public video search (WEB client, no auth). The WEB response carries each
     * result's upload date, which yt-dlp's flat `ytsearch` does not.
     */
    public suspend fun search(target: SearchTarget): InnerTubeResponse =
        execute(searchUrl, webContext(target.fields()), bearer = null)

    /** Follows a continuation token (e.g. loading comments; WEB client, no auth). */
    public suspend fun nextContinuation(continuation: String): InnerTubeResponse =
        execute(nextUrl, webContext(""" "continuation":"$continuation" """), bearer = null)

    /**
     * Authenticated write action (like, subscribe, comment, …) as the TV
     * client. [fieldsJson] is the request body minus the context (e.g.
     * `"target":{"videoId":"…"}`).
     */
    public suspend fun action(url: String, fieldsJson: String, accessToken: AccessToken): InnerTubeResponse =
        execute(url, tvContext(fieldsJson), accessToken)

    private fun tvContext(fields: String): String =
        """{"context":{"client":{"clientName":"TVHTML5","clientVersion":"$tvClientVersion"}},$fields}"""

    private fun androidContext(videoId: String): String =
        clientContext(
            """"clientName":"ANDROID","clientVersion":"$androidClientVersion","androidSdkVersion":34,"hl":"en"""",
            """"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true""",
        )

    private fun webContext(field: String): String =
        clientContext(""""clientName":"WEB","clientVersion":"$webClientVersion"""", field)

    private fun clientContext(client: String, fields: String): String =
        """{"context":{"client":{$client}},$fields}"""

    private suspend fun execute(url: String, jsonBody: String, bearer: AccessToken?): InnerTubeResponse =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON))
            if (bearer != null) builder.addHeader("Authorization", "Bearer ${bearer.value}")
            try {
                client.newCall(builder.build()).execute().use { response ->
                    val body = response.body.string()
                    when {
                        response.isSuccessful && body.isNotBlank() -> InnerTubeResponse.Success(body)
                        response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN ->
                            InnerTubeResponse.Unauthorized
                        else -> InnerTubeResponse.Failure("HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                InnerTubeResponse.Failure(e.message ?: "network error")
            }
        }

    public companion object {
        private const val BASE: String = "https://www.youtube.com/youtubei/v1"
        public const val BROWSE_URL: String = "$BASE/browse?prettyPrint=false"
        public const val NEXT_URL: String = "$BASE/next?prettyPrint=false"
        public const val SEARCH_URL: String = "$BASE/search?prettyPrint=false"
        public const val PLAYER_URL: String = "$BASE/player?prettyPrint=false"

        /** Matches yt-dlp's android client; YouTube rejects a stale one. */
        public const val ANDROID_CLIENT_VERSION: String = "20.10.38"
        public const val LIKE_URL: String = "$BASE/like/like?prettyPrint=false"
        public const val DISLIKE_URL: String = "$BASE/like/dislike?prettyPrint=false"
        public const val REMOVE_LIKE_URL: String = "$BASE/like/removelike?prettyPrint=false"
        public const val SUBSCRIBE_URL: String = "$BASE/subscription/subscribe?prettyPrint=false"
        public const val UNSUBSCRIBE_URL: String = "$BASE/subscription/unsubscribe?prettyPrint=false"
        public const val CREATE_COMMENT_URL: String = "$BASE/comment/create_comment?prettyPrint=false"
        public const val EDIT_PLAYLIST_URL: String = "$BASE/browse/edit_playlist?prettyPrint=false"
        public const val TV_CLIENT_VERSION: String = "7.20240401.10.00"
        public const val WEB_CLIENT_VERSION: String = "2.20240726.00.00"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val JSON = "application/json".toMediaType()
    }
}

/**
 * What to browse: a feed/channel id, or a continuation token for a later page. A sealed
 * pair rather than two nullable parameters, because sending both is meaningless — a
 * continuation already encodes what it continues — and this makes that unrepresentable.
 */
public sealed interface BrowseTarget {
    /**
     * [params] selects a channel tab (Videos/Shorts/Playlists); omit for the default.
     * [query] is only meaningful with the channel's Search tab params, which is the one
     * tab that takes an argument — searching within a channel is a browse, not a search.
     */
    public data class Id(
        public val browseId: String,
        public val params: String? = null,
        public val query: String? = null,
    ) : BrowseTarget

    public data class Continuation(public val token: String) : BrowseTarget
}

/**
 * What a search request asks for: a query, or the next page of one. Modelled exactly like
 * [BrowseTarget] and for the same reason — a continuation already encodes what it
 * continues, so a query alongside it would be meaningless.
 */
public sealed interface SearchTarget {
    public data class Query(public val text: String) : SearchTarget
    public data class Continuation(public val token: String) : SearchTarget
}

/**
 * The request-body fields that select this target. A query is arbitrary user text, so it
 * is JSON-encoded rather than interpolated; a token is YouTube's own opaque string.
 */
internal fun SearchTarget.fields(): String = when (this) {
    is SearchTarget.Query -> " \"query\":" + JsonPrimitive(text)
    is SearchTarget.Continuation -> """ "continuation":"$token" """
}

/** The request-body fields that select this target. */
internal fun BrowseTarget.fields(): String = when (this) {
    is BrowseTarget.Id -> buildString {
        append(""" "browseId":"$browseId" """)
        if (params != null) append(""", "params":"$params" """)
        // Arbitrary user text, so encoded rather than interpolated.
        if (query != null) append(", \"query\":" + JsonPrimitive(query))
    }
    is BrowseTarget.Continuation -> """ "continuation":"$token" """
}

/** Result of an InnerTube POST (browse or next). */
public sealed interface InnerTubeResponse {
    public data class Success(val body: String) : InnerTubeResponse

    /** The token was rejected — treat as signed out. */
    public data object Unauthorized : InnerTubeResponse

    public data class Failure(val detail: String) : InnerTubeResponse
}
