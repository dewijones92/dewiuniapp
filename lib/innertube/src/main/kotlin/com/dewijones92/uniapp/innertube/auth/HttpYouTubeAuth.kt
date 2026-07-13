package com.dewijones92.uniapp.innertube.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * OkHttp-backed [YouTubeAuth] speaking Google's device-code flow as the
 * public YouTube-on-TV client. Endpoints and clock are injectable for tests;
 * response mapping lives in [AuthResponseParser].
 */
public class HttpYouTubeAuth(
    private val client: OkHttpClient,
    private val credentials: OAuthClientCredentials = YouTubeTvClient.CREDENTIALS,
    private val deviceCodeUrl: String = YouTubeTvClient.DEVICE_CODE_URL,
    private val tokenUrl: String = YouTubeTvClient.TOKEN_URL,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) : YouTubeAuth {

    override suspend fun requestDeviceAuthorization(): DeviceAuthorizationResult {
        val form = FormBody.Builder()
            .add("client_id", credentials.clientId)
            .add("scope", YouTubeTvClient.SCOPE)
            .build()
        return when (val response = post(deviceCodeUrl, form)) {
            is PostResult.Body -> AuthResponseParser.parseDeviceAuthorization(response.text)
            is PostResult.Failure -> DeviceAuthorizationResult.Failure(response.detail)
        }
    }

    override suspend fun pollForTokens(deviceCode: DeviceCode): TokenPollResult {
        val form = FormBody.Builder()
            .add("client_id", credentials.clientId)
            .add("client_secret", credentials.clientSecret)
            .add("device_code", deviceCode.value)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        return when (val response = post(tokenUrl, form)) {
            is PostResult.Body -> AuthResponseParser.parseTokenPoll(response.text, nowEpochSeconds())
            is PostResult.Failure -> TokenPollResult.Failure(response.detail)
        }
    }

    override suspend fun refreshTokens(tokens: OAuthTokens): TokenRefreshResult {
        val form = FormBody.Builder()
            .add("client_id", credentials.clientId)
            .add("client_secret", credentials.clientSecret)
            .add("refresh_token", tokens.refreshToken.value)
            .add("grant_type", "refresh_token")
            .build()
        return when (val response = post(tokenUrl, form)) {
            is PostResult.Body ->
                AuthResponseParser.parseRefresh(response.text, nowEpochSeconds(), tokens.refreshToken)
            is PostResult.Failure -> TokenRefreshResult.Failure(response.detail)
        }
    }

    /**
     * OAuth errors ride on 4xx statuses with JSON bodies, so any non-blank
     * body goes to the parser regardless of status; only a body-less failure
     * becomes an HTTP-level error.
     */
    private suspend fun post(url: String, form: FormBody): PostResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).post(form).build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    body.isNotBlank() -> PostResult.Body(body)
                    response.isSuccessful -> PostResult.Failure("Empty response body")
                    else -> PostResult.Failure("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            PostResult.Failure(e.message ?: "network error")
        }
    }

    private sealed interface PostResult {
        data class Body(val text: String) : PostResult
        data class Failure(val detail: String) : PostResult
    }
}
