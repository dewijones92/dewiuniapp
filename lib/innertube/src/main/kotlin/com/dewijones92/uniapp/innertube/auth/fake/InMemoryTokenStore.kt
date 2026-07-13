package com.dewijones92.uniapp.innertube.auth.fake

import com.dewijones92.uniapp.innertube.auth.OAuthTokens
import com.dewijones92.uniapp.innertube.auth.TokenStore

/** In-memory [TokenStore] for tests and previews. */
public class InMemoryTokenStore(initial: OAuthTokens? = null) : TokenStore {

    private var tokens: OAuthTokens? = initial

    override suspend fun load(): OAuthTokens? = tokens

    override suspend fun save(tokens: OAuthTokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}
