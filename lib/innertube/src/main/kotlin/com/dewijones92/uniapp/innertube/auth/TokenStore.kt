package com.dewijones92.uniapp.innertube.auth

/**
 * Port for persisting the signed-in state. The library defines the seam; the
 * app supplies the storage (and tests supply
 * [com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore]).
 */
public interface TokenStore {
    public suspend fun load(): OAuthTokens?
    public suspend fun save(tokens: OAuthTokens)
    public suspend fun clear()
}
