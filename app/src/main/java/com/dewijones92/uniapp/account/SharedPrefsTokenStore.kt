package com.dewijones92.uniapp.account

import android.content.Context
import androidx.core.content.edit
import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.OAuthTokens
import com.dewijones92.uniapp.innertube.auth.RefreshToken
import com.dewijones92.uniapp.innertube.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [TokenStore] backed by a private [android.content.SharedPreferences] file.
 * The app-side storage the library's port asks for; the sign-in survives
 * restarts. IO hops off the main thread.
 */
class SharedPrefsTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences("youtube_account", Context.MODE_PRIVATE)

    override suspend fun load(): OAuthTokens? = withContext(Dispatchers.IO) {
        val access = prefs.getString(KEY_ACCESS, null)
        val refresh = prefs.getString(KEY_REFRESH, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, -1L)
        if (access == null || refresh == null || expiresAt < 0) {
            null
        } else {
            OAuthTokens(AccessToken(access), RefreshToken(refresh), expiresAt)
        }
    }

    override suspend fun save(tokens: OAuthTokens): Unit = withContext(Dispatchers.IO) {
        prefs.edit {
            putString(KEY_ACCESS, tokens.accessToken.value)
            putString(KEY_REFRESH, tokens.refreshToken.value)
            putLong(KEY_EXPIRES_AT, tokens.expiresAtEpochSeconds)
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit { clear() }
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
