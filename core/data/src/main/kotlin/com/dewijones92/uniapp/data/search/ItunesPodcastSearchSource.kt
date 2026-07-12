package com.dewijones92.uniapp.data.search

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.net.FetchResult
import com.dewijones92.uniapp.data.net.HttpTextFetcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Podcast directory search backed by the iTunes Search API — the standard
 * open endpoint podcast apps use; no key required.
 */
public class ItunesPodcastSearchSource(private val fetcher: HttpTextFetcher) : SearchSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: SearchQuery, limit: Int): SearchOutcome {
        val encoded = URLEncoder.encode(query.value, Charsets.UTF_8)
        val url = HttpUrl.of("https://itunes.apple.com/search?media=podcast&limit=$limit&term=$encoded")

        val body = when (val fetched = fetcher.fetch(url)) {
            is FetchResult.Success -> fetched.body
            is FetchResult.Failure -> return SearchOutcome.Failure(fetched.detail)
        }

        val results = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { return SearchOutcome.Failure("Unparseable directory response") }

        val hits = results["results"]?.jsonArray.orEmpty().mapNotNull { element ->
            val entry = element.jsonObject
            val feedUrl = entry.stringOrNull("feedUrl")?.let(HttpUrl::parse) ?: return@mapNotNull null
            SearchHit.Podcast(
                title = entry.stringOrNull("collectionName") ?: return@mapNotNull null,
                subtitle = entry.stringOrNull("artistName"),
                artworkUrl = entry.stringOrNull("artworkUrl600")?.let(HttpUrl::parse),
                feedUrl = feedUrl,
            )
        }
        return SearchOutcome.Success(hits)
    }

    private fun kotlinx.serialization.json.JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
}
