package com.dewijones92.uniapp.innertube.auth

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpYouTubeAuthTest {

    private val server = MockWebServer()
    private lateinit var auth: HttpYouTubeAuth

    @Before
    fun setUp() {
        server.start()
        auth = HttpYouTubeAuth(
            client = OkHttpClient(),
            deviceCodeUrl = server.url("/device/code").toString(),
            tokenUrl = server.url("/token").toString(),
            nowEpochSeconds = { NOW },
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `requests a device authorization with the TV client identity`() = runBlocking {
        server.enqueue(
            response(
                code = 200,
                body = """
                {"device_code":"d","user_code":"AAA-BBB","expires_in":1800,"interval":5,
                 "verification_url":"https://www.google.com/device"}
                """,
            ),
        )

        val result = auth.requestDeviceAuthorization()

        assertEquals("AAA-BBB", (result as DeviceAuthorizationResult.Started).authorization.userCode)
        val sent = server.takeRequest()
        val form = sent.body?.utf8().orEmpty()
        assertTrue(form.contains("client_id="))
        assertTrue(form.contains("scope="))
    }

    @Test
    fun `pending rides a 428 status and still parses`() = runBlocking {
        server.enqueue(
            response(code = 428, body = """{"error":"authorization_pending","error_description":"x"}"""),
        )

        assertEquals(TokenPollResult.Pending, auth.pollForTokens(DeviceCode("d")))
    }

    @Test
    fun `an authorized poll returns tokens stamped with the injected clock`() = runBlocking {
        server.enqueue(
            response(code = 200, body = """{"access_token":"at","expires_in":100,"refresh_token":"rt"}"""),
        )

        val result = auth.pollForTokens(DeviceCode("d"))

        assertEquals(NOW + 100, (result as TokenPollResult.Authorized).tokens.expiresAtEpochSeconds)
    }

    @Test
    fun `refresh sends the refresh grant and keeps the existing refresh token`() = runBlocking {
        server.enqueue(response(code = 200, body = """{"access_token":"fresh","expires_in":100}"""))

        val result = auth.refreshTokens(TOKENS)

        assertEquals("rt", (result as TokenRefreshResult.Refreshed).tokens.refreshToken.value)
        val form = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(form.contains("grant_type=refresh_token"))
    }

    @Test
    fun `a body-less server error becomes an HTTP failure value`() = runBlocking {
        server.enqueue(response(code = 500, body = ""))

        val result = auth.requestDeviceAuthorization()

        assertEquals("HTTP 500", (result as DeviceAuthorizationResult.Failure).detail)
    }

    @Test
    fun `a dead network becomes a failure value, not an exception`() = runBlocking {
        val url = server.url("/gone").toString()
        server.close()
        val deadAuth = HttpYouTubeAuth(
            client = OkHttpClient(),
            deviceCodeUrl = url,
            tokenUrl = url,
            nowEpochSeconds = { NOW },
        )

        assertTrue(deadAuth.requestDeviceAuthorization() is DeviceAuthorizationResult.Failure)
    }

    private fun response(code: Int, body: String): MockResponse =
        MockResponse.Builder().code(code).body(body).build()

    private companion object {
        const val NOW = 1_000_000L
        val TOKENS = OAuthTokens(
            accessToken = AccessToken("at"),
            refreshToken = RefreshToken("rt"),
            expiresAtEpochSeconds = 500L,
        )
    }
}
