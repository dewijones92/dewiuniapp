package com.dewijones92.totum.data.download

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reaching for the second path, and only when it could help.
 *
 * Novara's members-only uploads are the case: YouTube serves them to the signed-in app and refuses
 * them to yt-dlp, so they sat in Dewi's queue as "asking again cannot help" — correctly, because
 * nothing was ever going to ask differently (report 0.1.346).
 */
class FallbackDownloadStrategyTest {

    private val target = File("/tmp/does-not-matter.media")

    private val item = PlayableItem(
        MediaItem(
            id = MediaItemId("aaaaaaaaaaa"),
            sourceId = SourceId("src"),
            title = "AD FREE | something",
            publishedAt = null,
            duration = null,
        ),
        PlayHandle.Video(HttpUrl.of("https://www.youtube.com/watch?v=aaaaaaaaaaa")),
    )

    private fun strategy(
        primary: List<DownloadState>,
        secondary: List<DownloadState>,
        shouldFallBack: (DownloadState.Failed) -> Boolean = { true },
    ) = FallbackDownloadStrategy(
        primary = DownloadStrategy { _, _, _ -> flowOf(*primary.toTypedArray()) },
        secondary = DownloadStrategy { _, _, _ -> flowOf(*secondary.toTypedArray()) },
        shouldFallBack = shouldFallBack,
    )

    @Test
    fun `a refusal the second path could fix is retried there`() = runTest {
        val states = strategy(
            primary = listOf(DownloadState.Failed("This video is available to this channel's members")),
            secondary = listOf(DownloadState.Downloaded("/data/a.m4a", audioOnly = true)),
        ).download(item, target, audioOnly = true).toList()

        assertEquals(DownloadState.Downloaded("/data/a.m4a", audioOnly = true), states.last())
    }

    @Test
    fun `a refusal it could not fix is reported as it was`() = runTest {
        val refusal = DownloadState.Failed("Video unavailable")

        val states = strategy(
            primary = listOf(refusal),
            secondary = listOf(DownloadState.Downloaded("/data/a.m4a")),
            shouldFallBack = { false },
        ).download(item, target, audioOnly = true).toList()

        assertEquals("the second path must not even be tried", listOf(refusal), states)
    }

    @Test
    fun `a success on the first path is left completely alone`() = runTest {
        val done = DownloadState.Downloaded("/data/full.mkv")

        val states = strategy(
            primary = listOf(DownloadState.Downloading(1, 2), done),
            secondary = listOf(DownloadState.Failed("should never run")),
        ).download(item, target, audioOnly = false).toList()

        assertEquals(listOf(DownloadState.Downloading(1, 2), done), states)
    }

    /**
     * A download that reported 40% and then restarted from zero under a different mechanism would
     * read as broken, so the abandoned attempt's progress is dropped rather than forwarded.
     */
    @Test
    fun `progress from the attempt that failed is not shown`() = runTest {
        val states = strategy(
            primary = listOf(DownloadState.Downloading(50, 100), DownloadState.Failed("members only")),
            secondary = listOf(DownloadState.Downloading(1, 100), DownloadState.Downloaded("/data/a.m4a")),
        ).download(item, target, audioOnly = true).toList()

        assertEquals(
            "only the second attempt's progress should be visible",
            listOf(
                DownloadState.Downloading(50, 100),
                DownloadState.Downloading(1, 100),
                DownloadState.Downloaded("/data/a.m4a"),
            ),
            states,
        )
    }

    /** Both reasons, because the first says why the ordinary path could not do it. */
    @Test
    fun `when both fail the message keeps the original refusal`() = runTest {
        val states = strategy(
            primary = listOf(DownloadState.Failed("members only")),
            secondary = listOf(DownloadState.Failed("no SABR session for the resolved stream")),
        ).download(item, target, audioOnly = true).toList()

        val reason = (states.last() as DownloadState.Failed).reason
        assertTrue("the original refusal must survive: $reason", "members only" in reason)
        assertTrue("and so must the second: $reason", "no SABR session" in reason)
    }
}
