package com.dewijones92.uniapp.innertube.auth

import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAccountTest {

    private val auth = FakeYouTubeAuth()
    private val store = InMemoryTokenStore()
    private val account = YouTubeAccount(auth, store, nowEpochSeconds = { NOW })

    @Test
    fun `a successful sign-in is persisted`() = runTest {
        auth.pollResults.add(TokenPollResult.Authorized(FRESH))

        account.signIn().toList()

        assertEquals(FRESH, store.load())
        assertTrue(account.isSignedIn())
    }

    @Test
    fun `a failed sign-in persists nothing`() = runTest {
        auth.pollResults.add(TokenPollResult.Denied)

        account.signIn().toList()

        assertNull(store.load())
        assertFalse(account.isSignedIn())
    }

    @Test
    fun `sign-out clears the store`() = runTest {
        store.save(FRESH)

        account.signOut()

        assertFalse(account.isSignedIn())
    }

    @Test
    fun `a live token is served without touching the network`() = runTest {
        store.save(FRESH)

        val result = account.accessToken()

        assertEquals(AccessTokenResult.Available(FRESH.accessToken), result)
    }

    @Test
    fun `a stale token is refreshed and the refresh persisted`() = runTest {
        store.save(STALE)
        val renewed = FRESH.copy(accessToken = AccessToken("renewed"))
        auth.refreshResult = TokenRefreshResult.Refreshed(renewed)

        val result = account.accessToken()

        assertEquals(AccessTokenResult.Available(AccessToken("renewed")), result)
        assertEquals(renewed, store.load())
    }

    @Test
    fun `a token expiring within the safety margin is treated as stale`() = runTest {
        store.save(FRESH.copy(expiresAtEpochSeconds = NOW + 30))
        auth.refreshResult = TokenRefreshResult.Refreshed(FRESH)

        val result = account.accessToken()

        assertEquals(AccessTokenResult.Available(FRESH.accessToken), result)
    }

    @Test
    fun `a revoked grant signs the user out`() = runTest {
        store.save(STALE)
        auth.refreshResult = TokenRefreshResult.Revoked

        val result = account.accessToken()

        assertEquals(AccessTokenResult.SignedOut, result)
        assertNull(store.load())
    }

    @Test
    fun `a refresh failure is surfaced but keeps the sign-in`() = runTest {
        store.save(STALE)
        auth.refreshResult = TokenRefreshResult.Failure("offline")

        val result = account.accessToken()

        assertEquals(AccessTokenResult.Failure("offline"), result)
        assertEquals(STALE, store.load())
    }

    @Test
    fun `no tokens means signed out`() = runTest {
        assertEquals(AccessTokenResult.SignedOut, account.accessToken())
    }

    private companion object {
        const val NOW = 1_000_000L
        val FRESH = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = NOW + 3_600)
        val STALE = OAuthTokens(AccessToken("old"), RefreshToken("rt"), expiresAtEpochSeconds = NOW - 1)
    }
}
