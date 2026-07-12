package com.dewijones92.uniapp.data.sponsorblock

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.net.FetchResult
import com.dewijones92.uniapp.domain.SkipSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SponsorBlockSegmentSourceTest {

    @Test
    fun `parses segments, dropping malformed entries`() = runTest {
        val body = """
            [
              {"segment": [12.5, 45.0], "category": "sponsor"},
              {"segment": [100.0, 130.25], "category": "selfpromo"},
              {"segment": [50.0], "category": "broken"},
              {"category": "no-segment"},
              {"segment": [30.0, 20.0], "category": "inverted"}
            ]
        """.trimIndent()
        val source = SponsorBlockSegmentSource { FetchResult.Success(body) }

        val segments = source.segmentsFor("abc123")

        assertEquals(
            listOf(
                SkipSegment(12.5.seconds, 45.seconds),
                SkipSegment(100.seconds, 130.25.seconds),
            ),
            segments,
        )
    }

    @Test
    fun `requests the right video and categories`() = runTest {
        var requested: HttpUrl? = null
        val source = SponsorBlockSegmentSource { url ->
            requested = url
            FetchResult.Failure("HTTP 404")
        }

        source.segmentsFor("abc123")

        val url = checkNotNull(requested).value
        assertTrue(url.contains("videoID=abc123"))
        assertTrue(url.contains("category=sponsor"))
        assertTrue(url.contains("category=selfpromo"))
    }

    @Test
    fun `404, network failure, and garbage all fail open to no segments`() = runTest {
        assertTrue(SponsorBlockSegmentSource { FetchResult.Failure("HTTP 404") }.segmentsFor("x").isEmpty())
        assertTrue(SponsorBlockSegmentSource { FetchResult.Failure("timeout") }.segmentsFor("x").isEmpty())
        assertTrue(SponsorBlockSegmentSource { FetchResult.Success("<html>") }.segmentsFor("x").isEmpty())
    }
}
