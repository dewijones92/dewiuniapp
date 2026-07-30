package com.dewijones92.totum.innertube.browse

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Badge labels, read from the shape YouTube actually uses.
 *
 * Worth its own test because two of the three tile parsers previously guessed a DIFFERENT
 * shape and both guessed wrong — one read metadata text where the badge is a sibling
 * `badge` node, the other matched a `badgeViewModel` key that appears in no real response
 * and in none of the captured fixtures. Nothing failed; members-only simply never reported.
 */
class BadgesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String) =
        json.parseToJsonElement(
            checkNotNull(javaClass.getResourceAsStream("/$name")) { "fixture $name missing" }
                .bufferedReader().readText(),
        )

    @Test
    fun `reads TV tile badges, which sit beside the metadata rather than in it`() {
        val labels = Badges.labelsIn(fixture("tile_badges_tv_sample.json"))

        assertEquals(listOf("4K", "Members only"), labels)
    }

    @Test
    fun `reads badges out of a real captured search response`() {
        val labels = Badges.labelsIn(fixture("search_web_sample.json"))

        // Whatever YouTube tagged that day; the point is that real badges are found at all.
        assertTrue("expected some badge labels, got $labels", labels.isNotEmpty())
    }

    @Test
    fun `a response with no badges yields none rather than throwing`() {
        assertEquals(emptyList<String>(), Badges.labelsIn(fixture("feed_tv_sample.json")))
    }
}
