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

    /**
     * The player response as the SIGNED-IN account, which is the only way to reach an
     * age-restricted video.
     *
     * Measured from report 0.1.289: three items failed with *"Sign in to confirm your age… rated
     * 15… use --cookies"*. yt-dlp has no credentials and cannot be given any here, but the app
     * already holds a YouTube account for the TV device-code flow, and YouTube will serve a
     * rated video to a signed-in adult.
     *
     * It does NOT help with members-only videos, which failed in the same report with "join this
     * channel to get access". That is a genuine paywall rather than a missing credential, and no
     * token this app can hold will open it.
     *
     * Identical on the wire to [playerTracking] — same endpoint, same TV context, same
     * `racyCheckOk` — so this is a second NAME rather than a second request. Kept separate
     * because the two callers want different halves of one response, and a method called
     * "tracking" being used to fetch streams would mislead every reader after this one.
     */
    public suspend fun playerAsAccount(
        videoId: String,
        signatureTimestamp: Int,
        accessToken: AccessToken,
    ): InnerTubeResponse = playerTracking(videoId, signatureTimestamp, accessToken)

    /**
     * The player response as the EMBEDDED player client.
     *
     * A second attempt at age-restricted videos, and an honest experiment rather than a known
     * fix. Tested 2026-08-01 against a rated video with a valid signed-in token: the TVHTML5
     * client was refused outright, so signing in is evidently not sufficient on its own. The
     * embedded client is the identity that has historically been allowed to fetch rated
     * material, which is why it is worth one try.
     *
     * If YouTube refuses this too, the answer is that the app cannot play age-restricted videos
     * and the honest thing is to say so in the UI rather than keep adding client identities.
     */
    /**
     * The player response as the **ANDROID_VR** client.
     *
     * This is the identity that actually gets past the age gate, and it does so WITHOUT
     * credentials — which is why PipePipe and SmartTube can play rated videos and two rounds of
     * signed-in TV/embedded calls could not. The headset client is not age-gated the way the
     * phone and TV clients are.
     *
     * Authenticated, and that was measured rather than assumed. Sent WITHOUT a token first,
     * YouTube answered `LOGIN_REQUIRED: Sign in to confirm your age` — which is the request
     * being accepted and asked to identify itself, quite different from the TV client's flat
     * `UNPLAYABLE` and the embedded client's "no longer supported". The gate wants a signed-in
     * adult, and this client is the one willing to ask.
     */
    public suspend fun playerAndroidVr(videoId: String, accessToken: AccessToken?): InnerTubeResponse =
        execute(
            playerUrl,
            """{"context":{"client":{"clientName":"ANDROID_VR","clientVersion":"1.60.19",""" +
                """"deviceMake":"Oculus","deviceModel":"Quest 3","androidSdkVersion":32,""" +
                """"osName":"Android","osVersion":"12L","hl":"en"}},""" +
                """"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}""",
            accessToken,
        )

    public suspend fun playerEmbedded(videoId: String, accessToken: AccessToken?): InnerTubeResponse =
        execute(
            playerUrl,
            """{"context":{"client":{"clientName":"TVHTML5_SIMPLY_EMBEDDED_PLAYER",""" +
                """"clientVersion":"2.0","clientScreen":"EMBED"},""" +
                """"thirdParty":{"embedUrl":"https://www.youtube.com/"}},""" +
                """"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}""",
            accessToken,
        )

    /**
     * A video's **playback-tracking** URLs, as the signed-in TV client.
     *
     * A separate call from [player] on purpose: this one is authenticated and returns no
     * fetchable stream URLs at all (the TV client is SABR-only — 27 formats, none with a
     * url, measured 2026-07-31), while [player] is anonymous and exists purely for streams.
     * One request cannot be both, and the app needs both.
     *
     * [signatureTimestamp] is not optional in practice. Without it — or with a stale value —
     * YouTube answers UNPLAYABLE "The page needs to be reloaded" even with a valid token,
     * which is why the app's watch-history sync silently credited nobody for so long.
     */
    public suspend fun playerTracking(
        videoId: String,
        signatureTimestamp: Int,
        accessToken: AccessToken,
    ): InnerTubeResponse =
        execute(
            playerUrl,
            tvContext(
                """ "videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true,""" +
                    """"playbackContext":{"contentPlaybackContext":""" +
                    """{"html5Preference":"HTML5_PREF_WANTS","signatureTimestamp":$signatureTimestamp}} """,
            ),
            accessToken,
        )

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
