package com.dewijones92.uniapp.innertube.auth

/** Bearer token for authenticated InnerTube calls. Redacted in logs. */
@JvmInline
public value class AccessToken(public val value: String) {
    override fun toString(): String = "AccessToken(redacted)"
}

/** Long-lived token that mints fresh [AccessToken]s. Redacted in logs. */
@JvmInline
public value class RefreshToken(public val value: String) {
    override fun toString(): String = "RefreshToken(redacted)"
}

/**
 * The credential pair a completed sign-in yields. The access token expires
 * (typically within the hour); the refresh token outlives it and is the thing
 * worth persisting.
 */
public data class OAuthTokens(
    val accessToken: AccessToken,
    val refreshToken: RefreshToken,
    val expiresAtEpochSeconds: Long,
) {
    public fun isExpired(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= expiresAtEpochSeconds
}
