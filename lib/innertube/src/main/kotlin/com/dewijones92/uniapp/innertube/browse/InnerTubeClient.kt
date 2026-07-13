package com.dewijones92.uniapp.innertube.browse

import com.dewijones92.uniapp.innertube.auth.AccessToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Minimal authenticated client for YouTube's private InnerTube `browse`
 * endpoint, impersonating the living-room TV app — the same client our
 * device-code OAuth authenticates as (the WEB client rejects a TV token,
 * verified against YouTube). One call shape serves every account feed
 * (subscriptions, history, watch later, …); callers pass the browse id.
 */
public class InnerTubeClient(
    private val client: OkHttpClient,
    private val baseUrl: String = BROWSE_URL,
    private val clientVersion: String = TV_CLIENT_VERSION,
) {

    public suspend fun browse(browseId: String, accessToken: AccessToken): BrowseResult =
        withContext(Dispatchers.IO) {
            val payload = """
                {"context":{"client":{"clientName":"TVHTML5","clientVersion":"$clientVersion"}},
                 "browseId":"$browseId"}
            """.trimIndent().toRequestBody(JSON)
            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer ${accessToken.value}")
                .addHeader("Content-Type", "application/json")
                .post(payload)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    when {
                        response.isSuccessful && body.isNotBlank() -> BrowseResult.Success(body)
                        response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN ->
                            BrowseResult.Unauthorized
                        else -> BrowseResult.Failure("HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                BrowseResult.Failure(e.message ?: "network error")
            }
        }

    public companion object {
        public const val BROWSE_URL: String = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
        public const val TV_CLIENT_VERSION: String = "7.20240401.10.00"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val JSON = "application/json".toMediaType()
    }
}

public sealed interface BrowseResult {
    public data class Success(val body: String) : BrowseResult

    /** The token was rejected — treat as signed out. */
    public data object Unauthorized : BrowseResult

    public data class Failure(val detail: String) : BrowseResult
}
