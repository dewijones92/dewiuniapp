package com.dewijones92.uniapp.data.rss

import com.dewijones92.uniapp.domain.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PodcastChaptersJsonTest {

    @Test
    fun `parses Podcasting 2_0 chapters, allowing fractional and zero starts`() {
        val json = """
            {"version":"1.2.0","chapters":[
              {"startTime":0,"title":"Intro"},
              {"startTime":90.5,"title":"Main topic","img":"https://x/a.jpg"},
              {"startTime":600,"title":"Wrap up"}
            ]}
        """.trimIndent()

        val chapters = PodcastChaptersJson.parse(json)

        assertEquals(
            listOf(
                Chapter(0.seconds, "Intro"),
                Chapter(90.seconds + 500.milliseconds, "Main topic"),
                Chapter(600.seconds, "Wrap up"),
            ),
            chapters,
        )
    }

    @Test
    fun `drops elements missing a start or title, and tolerates malformed input`() {
        val json = """
            {"chapters":[
              {"startTime":10,"title":"Kept"},
              {"title":"no start"},
              {"startTime":20},
              {"startTime":-5,"title":"negative"}
            ]}
        """.trimIndent()

        assertEquals(listOf(Chapter(10.seconds, "Kept")), PodcastChaptersJson.parse(json))
        assertTrue(PodcastChaptersJson.parse("not json").isEmpty())
        assertTrue(PodcastChaptersJson.parse("""{"no":"chapters key"}""").isEmpty())
    }
}
