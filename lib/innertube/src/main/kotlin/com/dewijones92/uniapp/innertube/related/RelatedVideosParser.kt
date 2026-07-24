package com.dewijones92.uniapp.innertube.related

import com.dewijones92.uniapp.innertube.feeds.LockupParser
import kotlinx.serialization.json.Json

/**
 * Extracts the related / "up next" videos from a WEB `next` (watch-page)
 * response. YouTube serves these as `lockupViewModel` tiles under the secondary
 * results; the shared [LockupParser] does the walking/mapping (the same shape
 * powers channel tabs), so this only wraps it in a [RelatedResult].
 * Shape verified against a real watch-page response (2026-07-14).
 */
internal object RelatedVideosParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): RelatedResult {
        runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return RelatedResult.Failure("Unparseable watch-page response")
        return RelatedResult.Success(LockupParser.videos(body))
    }
}
