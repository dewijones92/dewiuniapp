package com.dewijones92.uniapp.data.sponsorblock

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.net.FetchResult
import com.dewijones92.uniapp.data.net.HttpTextFetcher
import com.dewijones92.uniapp.domain.SkipSegment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

/** Port for looking up skip-worthy segments of a video. */
public fun interface SkipSegmentSource {
    /** Segments for [videoId]; empty when there are none or lookup fails (fail-open enhancement). */
    public suspend fun segmentsFor(videoId: String): List<SkipSegment>
}

/**
 * SponsorBlock-backed [SkipSegmentSource] (community-crowdsourced segments,
 * keyless API). Any failure — network, 404 (no segments), unparseable body —
 * yields an empty list: skipping is an enhancement, never a blocker.
 */
public class SponsorBlockSegmentSource(private val fetcher: HttpTextFetcher) : SkipSegmentSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun segmentsFor(videoId: String): List<SkipSegment> {
        val encoded = URLEncoder.encode(videoId, Charsets.UTF_8)
        val categories = CATEGORIES.joinToString("&") { "category=$it" }
        val url = HttpUrl.of("https://sponsor.ajay.app/api/skipSegments?videoID=$encoded&$categories")

        val body = when (val fetched = fetcher.fetch(url)) {
            is FetchResult.Success -> fetched.body
            is FetchResult.Failure -> return emptyList()
        }

        return runCatching {
            json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
                val pair = element.jsonObject["segment"]?.jsonArray ?: return@mapNotNull null
                val start = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val end = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                if (end > start && start >= 0) SkipSegment(start.seconds, end.seconds) else null
            }
        }.getOrDefault(emptyList())
    }

    public companion object {
        /**
         * The unambiguous "not the content" categories. The single source of
         * truth for what the app treats as skippable — used both to fetch
         * segments for playback-time skipping and to remove them from downloads.
         */
        public val CATEGORIES: List<String> = listOf("sponsor", "selfpromo", "interaction")
    }
}
