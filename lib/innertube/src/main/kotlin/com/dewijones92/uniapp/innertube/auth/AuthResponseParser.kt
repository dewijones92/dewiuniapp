package com.dewijones92.uniapp.innertube.auth

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure mapping from Google's OAuth JSON bodies to sealed results. Kept free
 * of IO so the protocol knowledge is unit-testable; OAuth errors arrive as
 * `{"error": "..."}` bodies (on 4xx statuses), so status codes are not needed
 * here.
 */
internal object AuthResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseDeviceAuthorization(body: String): DeviceAuthorizationResult {
        val fields = body.asJsonObject()
            ?: return DeviceAuthorizationResult.Failure("Unparseable device authorization response")
        fields.stringOrNull("error")?.let { return DeviceAuthorizationResult.Failure(fields.errorDetail()) }
        val authorization = fields.deviceAuthorizationOrNull()
        return if (authorization == null) {
            DeviceAuthorizationResult.Failure("Malformed device authorization response")
        } else {
            DeviceAuthorizationResult.Started(authorization)
        }
    }

    fun parseTokenPoll(body: String, nowEpochSeconds: Long): TokenPollResult {
        val fields = body.asJsonObject()
            ?: return TokenPollResult.Failure("Unparseable token response")
        fields.stringOrNull("error")?.let { return it.toPollResult(fields) }
        val refreshToken = fields.stringOrNull("refresh_token")?.let(::RefreshToken)
        val tokens = refreshToken?.let { fields.accessTokens(nowEpochSeconds, it) }
        return if (tokens == null) {
            TokenPollResult.Failure("Missing access_token/refresh_token/expires_in")
        } else {
            TokenPollResult.Authorized(tokens)
        }
    }

    fun parseRefresh(body: String, nowEpochSeconds: Long, existing: RefreshToken): TokenRefreshResult {
        val fields = body.asJsonObject()
            ?: return TokenRefreshResult.Failure("Unparseable refresh response")
        when (fields.stringOrNull("error")) {
            null -> Unit
            // invalid_grant = the refresh token itself is dead (revoked/expired).
            "invalid_grant" -> return TokenRefreshResult.Revoked
            else -> return TokenRefreshResult.Failure(fields.errorDetail())
        }
        // Refresh responses usually omit refresh_token; the existing one stays valid.
        val refreshToken = fields.stringOrNull("refresh_token")?.let(::RefreshToken) ?: existing
        val tokens = fields.accessTokens(nowEpochSeconds, refreshToken)
            ?: return TokenRefreshResult.Failure("Missing access_token/expires_in")
        return TokenRefreshResult.Refreshed(tokens)
    }

    private fun JsonObject.deviceAuthorizationOrNull(): DeviceAuthorization? {
        val deviceCode = stringOrNull("device_code") ?: return null
        val userCode = stringOrNull("user_code") ?: return null
        val verificationUrl = stringOrNull("verification_url")?.let(HttpUrl::parse) ?: return null
        val expiresIn = intOrNull("expires_in") ?: return null
        return DeviceAuthorization(
            deviceCode = DeviceCode(deviceCode),
            userCode = userCode,
            verificationUrl = verificationUrl,
            expiresInSeconds = expiresIn,
            pollIntervalSeconds = intOrNull("interval") ?: DEFAULT_POLL_INTERVAL_SECONDS,
        )
    }

    private fun String.toPollResult(fields: JsonObject): TokenPollResult = when (this) {
        "authorization_pending" -> TokenPollResult.Pending
        "slow_down" -> TokenPollResult.SlowDown
        "access_denied" -> TokenPollResult.Denied
        "expired_token" -> TokenPollResult.Expired
        else -> TokenPollResult.Failure(fields.errorDetail())
    }

    private fun JsonObject.accessTokens(nowEpochSeconds: Long, refreshToken: RefreshToken): OAuthTokens? {
        val accessToken = stringOrNull("access_token") ?: return null
        val expiresIn = intOrNull("expires_in") ?: return null
        return OAuthTokens(
            accessToken = AccessToken(accessToken),
            refreshToken = refreshToken,
            expiresAtEpochSeconds = nowEpochSeconds + expiresIn,
        )
    }

    private fun String.asJsonObject(): JsonObject? =
        runCatching { json.parseToJsonElement(this).jsonObject }.getOrNull()

    private fun JsonObject.errorDetail(): String {
        val error = stringOrNull("error") ?: "unknown error"
        val description = stringOrNull("error_description")
        return if (description == null) error else "$error: $description"
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }

    private fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private const val DEFAULT_POLL_INTERVAL_SECONDS = 5
}
