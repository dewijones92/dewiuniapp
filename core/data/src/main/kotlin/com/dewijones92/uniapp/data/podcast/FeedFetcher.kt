package com.dewijones92.uniapp.data.podcast

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Port for fetching feed documents; the repository never talks HTTP directly. */
public fun interface FeedFetcher {
    public suspend fun fetch(url: HttpUrl): FetchResult
}

public sealed interface FetchResult {
    public data class Success(val body: String) : FetchResult
    public data class Failure(val detail: String) : FetchResult
}

/** OkHttp-backed [FeedFetcher]. */
public class OkHttpFeedFetcher(private val client: OkHttpClient) : FeedFetcher {

    override suspend fun fetch(url: HttpUrl): FetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url.value).build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (response.isSuccessful) {
                    FetchResult.Success(body)
                } else {
                    FetchResult.Failure("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            FetchResult.Failure(e.message ?: "network error")
        }
    }
}
