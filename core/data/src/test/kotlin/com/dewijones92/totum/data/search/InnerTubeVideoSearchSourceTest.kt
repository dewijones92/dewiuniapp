package com.dewijones92.totum.data.search

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.innertube.search.SearchedVideo
import com.dewijones92.totum.innertube.search.fake.FakeYouTubeSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The middle of the chain that makes "go to channel" free from a search result.
 *
 * The parser reads the channel id and the locator prefers it over the engine; this is the link
 * between them, and it is exactly where the id used to be dropped. Worth its own test because
 * both tested ends would have stayed green while the hit in between carried nothing.
 */
class InnerTubeVideoSearchSourceTest {

    private val search = FakeYouTubeSearch()
    private val source = InnerTubeVideoSearchSource(search)

    @Test
    fun `a hit's channel id becomes a channel URL the locator can use`() = runTest {
        search.registerVideos(listOf(searched(channelId = "UCaaaaaaaaaaaaaaaaaaaaaa")))

        val video = firstHit()

        assertEquals("https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa", video.channelUrl?.value)
    }

    /** A collaboration names no single channel; the engine fallback handles those. */
    @Test
    fun `a hit with no channel id carries no URL rather than a broken one`() = runTest {
        search.registerVideos(listOf(searched(channelId = null)))

        assertNull(firstHit().channelUrl)
    }

    private suspend fun firstHit(): SearchHit.Video {
        val outcome = source.search(SearchQuery("q"), limit = 5, after = null)
        return (outcome as SearchOutcome.Success).page.items.single() as SearchHit.Video
    }

    private fun searched(channelId: String?) = SearchedVideo(
        videoId = "v1",
        title = "A video",
        author = "A channel",
        publishedText = "2 days ago",
        durationSeconds = 90,
        thumbnailUrl = null,
        watchUrl = HttpUrl.of("https://www.youtube.com/watch?v=v1"),
        channelId = channelId,
    )
}
