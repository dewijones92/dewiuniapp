package com.dewijones92.totum.data.search

/**
 * Tries [primary] and falls back to [fallback] when it fails or finds nothing.
 * Keeps two independent backends for one pillar useful without either knowing
 * about the other — used so InnerTube search (which carries upload dates) can
 * fail over to the yt-dlp engine if YouTube changes the response shape.
 */
public class FallbackSearchSource(
    private val primary: SearchSource,
    private val fallback: SearchSource,
) : SearchSource {

    override suspend fun search(query: SearchQuery, limit: Int): SearchOutcome =
        when (val result = primary.search(query, limit)) {
            is SearchOutcome.Success -> if (result.hits.isEmpty()) fallback.search(query, limit) else result
            is SearchOutcome.Failure -> fallback.search(query, limit)
        }
}
