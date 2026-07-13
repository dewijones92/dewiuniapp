package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RoutedDownloadStrategyTest {

    private fun item(url: String) = MediaItem(
        id = MediaItemId(url),
        sourceId = SourceId("s"),
        title = "t",
        publishedAt = null,
        duration = null,
        mediaUrl = HttpUrl.of(url),
    )

    private fun labelled(label: String) =
        DownloadStrategy { _, _ -> flowOf(DownloadState.Downloaded(label)) }

    private val routed = RoutedDownloadStrategy(
        routes = listOf({ i: MediaItem -> i.mediaUrl?.value?.contains("match") == true } to labelled("routed")),
        fallback = labelled("fallback"),
    )

    @Test
    fun `uses the first matching route`() = runTest {
        val state = routed.download(item("https://x/match"), File("t")).first()
        assertEquals("routed", (state as DownloadState.Downloaded).localPath)
    }

    @Test
    fun `falls back when no route matches`() = runTest {
        val state = routed.download(item("https://x/other"), File("t")).first()
        assertEquals("fallback", (state as DownloadState.Downloaded).localPath)
    }
}
