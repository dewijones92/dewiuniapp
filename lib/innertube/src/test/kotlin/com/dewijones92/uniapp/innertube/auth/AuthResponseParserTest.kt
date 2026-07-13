package com.dewijones92.uniapp.innertube.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthResponseParserTest {

    // --- device authorization ---

    @Test
    fun `parses a device authorization`() {
        val result = AuthResponseParser.parseDeviceAuthorization(
            """
            {
              "device_code": "AH-1Ng1d2S",
              "user_code": "BWM-XHD-XJBG",
              "expires_in": 1800,
              "interval": 5,
              "verification_url": "https://www.google.com/device"
            }
            """,
        )

        val started = result as DeviceAuthorizationResult.Started
        assertEquals("BWM-XHD-XJBG", started.authorization.userCode)
        assertEquals("AH-1Ng1d2S", started.authorization.deviceCode.value)
        assertEquals("https://www.google.com/device", started.authorization.verificationUrl.value)
        assertEquals(1800, started.authorization.expiresInSeconds)
        assertEquals(5, started.authorization.pollIntervalSeconds)
    }

    @Test
    fun `missing interval falls back to a sane default`() {
        val result = AuthResponseParser.parseDeviceAuthorization(
            """{"device_code":"d","user_code":"u","expires_in":600,"verification_url":"https://g.co/device"}""",
        )

        assertEquals(5, (result as DeviceAuthorizationResult.Started).authorization.pollIntervalSeconds)
    }

    @Test
    fun `device authorization failures are values, not exceptions`() {
        assertTrue(AuthResponseParser.parseDeviceAuthorization("not json") is DeviceAuthorizationResult.Failure)
        assertTrue(
            AuthResponseParser.parseDeviceAuthorization("""{"user_code":"u"}""")
            is DeviceAuthorizationResult.Failure,
        )
        val error = AuthResponseParser.parseDeviceAuthorization("""{"error":"invalid_client"}""")
        assertTrue((error as DeviceAuthorizationResult.Failure).detail.contains("invalid_client"))
    }

    // --- token polling ---

    @Test
    fun `pending, slow_down, denied and expired map to their poll results`() {
        assertEquals(
            TokenPollResult.Pending,
            AuthResponseParser.parseTokenPoll("""{"error":"authorization_pending"}""", NOW),
        )
        assertEquals(
            TokenPollResult.SlowDown,
            AuthResponseParser.parseTokenPoll("""{"error":"slow_down"}""", NOW),
        )
        assertEquals(
            TokenPollResult.Denied,
            AuthResponseParser.parseTokenPoll("""{"error":"access_denied"}""", NOW),
        )
        assertEquals(
            TokenPollResult.Expired,
            AuthResponseParser.parseTokenPoll("""{"error":"expired_token"}""", NOW),
        )
    }

    @Test
    fun `authorized poll yields tokens with an absolute expiry`() {
        val result = AuthResponseParser.parseTokenPoll(
            """{"access_token":"at","expires_in":3599,"refresh_token":"rt","token_type":"Bearer"}""",
            NOW,
        )

        val tokens = (result as TokenPollResult.Authorized).tokens
        assertEquals("at", tokens.accessToken.value)
        assertEquals("rt", tokens.refreshToken.value)
        assertEquals(NOW + 3599, tokens.expiresAtEpochSeconds)
        assertTrue(tokens.isExpired(NOW + 3599))
        assertTrue(!tokens.isExpired(NOW + 3598))
    }

    @Test
    fun `poll without refresh_token is a failure, and unknown errors carry detail`() {
        assertTrue(
            AuthResponseParser.parseTokenPoll("""{"access_token":"at","expires_in":10}""", NOW)
            is TokenPollResult.Failure,
        )
        val unknown = AuthResponseParser.parseTokenPoll(
            """{"error":"invalid_client","error_description":"Unauthorized"}""",
            NOW,
        )
        assertEquals("invalid_client: Unauthorized", (unknown as TokenPollResult.Failure).detail)
        assertTrue(AuthResponseParser.parseTokenPoll("garbage", NOW) is TokenPollResult.Failure)
    }

    // --- refresh ---

    @Test
    fun `refresh keeps the existing refresh token when the response omits it`() {
        val result = AuthResponseParser.parseRefresh(
            """{"access_token":"fresh","expires_in":3599}""",
            NOW,
            RefreshToken("kept"),
        )

        val tokens = (result as TokenRefreshResult.Refreshed).tokens
        assertEquals("fresh", tokens.accessToken.value)
        assertEquals("kept", tokens.refreshToken.value)
    }

    @Test
    fun `invalid_grant on refresh means the sign-in is dead`() {
        assertEquals(
            TokenRefreshResult.Revoked,
            AuthResponseParser.parseRefresh("""{"error":"invalid_grant"}""", NOW, RefreshToken("rt")),
        )
    }

    @Test
    fun `refresh failures are values`() {
        assertTrue(
            AuthResponseParser.parseRefresh("nope", NOW, RefreshToken("rt")) is TokenRefreshResult.Failure,
        )
        assertTrue(
            AuthResponseParser.parseRefresh("""{"error":"invalid_client"}""", NOW, RefreshToken("rt"))
            is TokenRefreshResult.Failure,
        )
    }

    @Test
    fun `tokens never leak into logs`() {
        assertEquals("AccessToken(redacted)", AccessToken("secret").toString())
        assertEquals("RefreshToken(redacted)", RefreshToken("secret").toString())
        assertEquals("DeviceCode(redacted)", DeviceCode("secret").toString())
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
