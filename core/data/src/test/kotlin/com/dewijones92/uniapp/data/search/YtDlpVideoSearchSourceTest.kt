package com.dewijones92.uniapp.data.search

import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpVideoSearchSourceTest {

    private val engine = FakeYtDlpEngine()
    private val source = YtDlpVideoSearchSource(engine)

    @Test
    fun `maps engine entries to video hits`() = runTest {
        engine.registerSearch("cats", listOf(FakeYtDlpEngine.sampleSearchEntry(id = "v1", title = "Cats!")))

        val hits = (source.search(SearchQuery("cats"), limit = 5) as SearchOutcome.Success).hits

        assertEquals(1, hits.size)
        val video = hits[0] as SearchHit.Video
        assertEquals("Cats!", video.title)
        assertEquals("Sample channel", video.subtitle)
        assertEquals("https://example.com/watch?v=v1", video.watchUrl.value)
        assertEquals(90L, video.durationSeconds)
    }

    @Test
    fun `unregistered query returns empty success`() = runTest {
        val outcome = source.search(SearchQuery("nothing"), limit = 5)
        assertTrue((outcome as SearchOutcome.Success).hits.isEmpty())
    }
}
