package com.dewijones92.uniapp.innertube.auth

/**
 * Port for Google's OAuth device-code flow — the "go to google.com/device and
 * type this code" pairing real TVs use. The only seam that talks to Google's
 * auth endpoints; everything above it works with sealed results and fakes.
 */
public interface YouTubeAuth {

    /** Starts a sign-in: asks Google for a fresh user code + device code. */
    public suspend fun requestDeviceAuthorization(): DeviceAuthorizationResult

    /** One poll of the token endpoint while the user is (maybe) approving. */
    public suspend fun pollForTokens(deviceCode: DeviceCode): TokenPollResult

    /** Exchanges the refresh token for a fresh access token. */
    public suspend fun refreshTokens(tokens: OAuthTokens): TokenRefreshResult
}

public sealed interface DeviceAuthorizationResult {
    public data class Started(val authorization: DeviceAuthorization) : DeviceAuthorizationResult
    public data class Failure(val detail: String) : DeviceAuthorizationResult
}

public sealed interface TokenPollResult {
    public data class Authorized(val tokens: OAuthTokens) : TokenPollResult

    /** The user hasn't finished approving yet; keep polling. */
    public data object Pending : TokenPollResult

    /** Google asked for a longer poll interval. */
    public data object SlowDown : TokenPollResult

    /** The user rejected the sign-in. */
    public data object Denied : TokenPollResult

    /** The code lapsed before the user finished; start over. */
    public data object Expired : TokenPollResult

    public data class Failure(val detail: String) : TokenPollResult
}

public sealed interface TokenRefreshResult {
    public data class Refreshed(val tokens: OAuthTokens) : TokenRefreshResult

    /** The grant was revoked (or is otherwise dead); the user must sign in again. */
    public data object Revoked : TokenRefreshResult

    public data class Failure(val detail: String) : TokenRefreshResult
}
