package com.dewijones92.totum.innertube.auth.fake

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.innertube.auth.DeviceAuthorization
import com.dewijones92.totum.innertube.auth.DeviceAuthorizationResult
import com.dewijones92.totum.innertube.auth.DeviceCode
import com.dewijones92.totum.innertube.auth.OAuthTokens
import com.dewijones92.totum.innertube.auth.TokenPollResult
import com.dewijones92.totum.innertube.auth.TokenRefreshResult
import com.dewijones92.totum.innertube.auth.YouTubeAuth

/** Scriptable [YouTubeAuth] for tests and previews; no network. */
public class FakeYouTubeAuth : YouTubeAuth {

    public var authorizationResult: DeviceAuthorizationResult =
        DeviceAuthorizationResult.Started(DEFAULT_AUTHORIZATION)

    /** Consumed one per poll; when empty, polls report [TokenPollResult.Pending]. */
    public val pollResults: ArrayDeque<TokenPollResult> = ArrayDeque()

    public var refreshResult: TokenRefreshResult = TokenRefreshResult.Revoked

    public var pollCount: Int = 0
        private set

    override suspend fun requestDeviceAuthorization(): DeviceAuthorizationResult = authorizationResult

    override suspend fun pollForTokens(deviceCode: DeviceCode): TokenPollResult {
        pollCount++
        return pollResults.removeFirstOrNull() ?: TokenPollResult.Pending
    }

    override suspend fun refreshTokens(tokens: OAuthTokens): TokenRefreshResult = refreshResult

    public companion object {
        public val DEFAULT_AUTHORIZATION: DeviceAuthorization = DeviceAuthorization(
            deviceCode = DeviceCode("fake-device-code"),
            userCode = "ABC-DEF-GHI",
            verificationUrl = HttpUrl.of("https://www.google.com/device"),
            expiresInSeconds = 1800,
            pollIntervalSeconds = 5,
        )
    }
}
