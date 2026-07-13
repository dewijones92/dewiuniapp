package com.dewijones92.uniapp.innertube.auth

import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLoginTest {

    private val auth = FakeYouTubeAuth()
    private val login = DeviceLogin(auth)

    @Test
    fun `surfaces the user code then succeeds once the user approves`() = runTest {
        auth.pollResults.addAll(listOf(TokenPollResult.Pending, TokenPollResult.Authorized(TOKENS)))

        val events = login.start().toList()

        assertEquals(
            listOf<DeviceLoginEvent>(
                DeviceLoginEvent.AwaitingUser(
                    FakeYouTubeAuth.DEFAULT_AUTHORIZATION.userCode,
                    FakeYouTubeAuth.DEFAULT_AUTHORIZATION.verificationUrl,
                ),
                DeviceLoginEvent.Succeeded(TOKENS),
            ),
            events,
        )
        // Two polls, five virtual seconds apart each.
        assertEquals(2, auth.pollCount)
        assertEquals(10_000L, currentTime)
    }

    @Test
    fun `slow_down stretches the polling interval`() = runTest {
        auth.pollResults.addAll(listOf(TokenPollResult.SlowDown, TokenPollResult.Authorized(TOKENS)))

        login.start().toList()

        // First poll at 5s; slow_down makes the next gap 10s.
        assertEquals(15_000L, currentTime)
    }

    @Test
    fun `a denied sign-in fails with Denied`() = runTest {
        auth.pollResults.add(TokenPollResult.Denied)

        val events = login.start().toList()

        assertEquals(DeviceLoginEvent.Failed(LoginFailure.Denied), events.last())
    }

    @Test
    fun `an unapproved code eventually expires`() = runTest {
        auth.authorizationResult = DeviceAuthorizationResult.Started(
            FakeYouTubeAuth.DEFAULT_AUTHORIZATION.copy(expiresInSeconds = 12),
        )

        val events = login.start().toList()

        assertEquals(DeviceLoginEvent.Failed(LoginFailure.Expired), events.last())
        assertEquals(3, auth.pollCount)
    }

    @Test
    fun `transient poll failures are retried, not fatal`() = runTest {
        auth.pollResults.addAll(
            listOf(TokenPollResult.Failure("blip"), TokenPollResult.Authorized(TOKENS)),
        )

        val events = login.start().toList()

        assertEquals(DeviceLoginEvent.Succeeded(TOKENS), events.last())
    }

    @Test
    fun `failing to even get a code fails with Network`() = runTest {
        auth.authorizationResult = DeviceAuthorizationResult.Failure("no route")

        val events = login.start().toList()

        assertEquals(listOf<DeviceLoginEvent>(DeviceLoginEvent.Failed(LoginFailure.Network("no route"))), events)
        assertTrue(auth.pollCount == 0)
    }

    private companion object {
        val TOKENS = OAuthTokens(
            accessToken = AccessToken("at"),
            refreshToken = RefreshToken("rt"),
            expiresAtEpochSeconds = 2_000_000L,
        )
    }
}
