package com.dewijones92.uniapp.innertube.auth

import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryTokenStoreTest {

    @Test
    fun `starts empty, remembers a save, forgets on clear`() = runTest {
        val store = InMemoryTokenStore()
        assertNull(store.load())

        val tokens = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 1L)
        store.save(tokens)
        assertEquals(tokens, store.load())

        store.clear()
        assertNull(store.load())
    }
}
