package com.dewijones92.totum.playback

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NextUpPrefetcherTest {

    private val states = MutableStateFlow<PlaybackState?>(null)
    private val prefetched = mutableListOf<PlayableItem>()
    private var next: PlayableItem? = video("next")

    private fun TestScope.prefetcher() = NextUpPrefetcher(
        states = states,
        nextUp = { next },
        prefetch = { prefetched += it },
        scope = backgroundScope,
        leadMs = 45_000,
    ).also { it.start() }

    @Test
    fun `does not resolve early in the item`() = runTest {
        prefetcher()
        states.value = playing(positionMs = 0, durationMs = 600_000)
        runCurrent()

        assertEquals(emptyList<String>(), prefetched.map { it.item.title })
    }

    @Test
    fun `resolves the next item as the current one nears its end`() = runTest {
        prefetcher()
        states.value = playing(positionMs = 580_000, durationMs = 600_000)
        runCurrent()

        assertEquals(listOf("next"), prefetched.map { it.item.title })
    }

    @Test
    fun `resolves once per item, however many positions arrive`() = runTest {
        prefetcher()
        repeat(5) { tick ->
            states.value = playing(positionMs = 580_000 + tick * 1_000L, durationMs = 600_000)
            runCurrent()
        }

        assertEquals(1, prefetched.size)
    }

    @Test
    fun `resolves again for the item after it`() = runTest {
        prefetcher()
        states.value = playing(positionMs = 580_000, durationMs = 600_000)
        runCurrent()

        next = video("after that")
        states.value = playing(id = "second", positionMs = 580_000, durationMs = 600_000)
        runCurrent()

        assertEquals(listOf("next", "after that"), prefetched.map { it.item.title })
    }

    @Test
    fun `a live stream, which has no duration, is left alone`() = runTest {
        prefetcher()
        states.value = playing(positionMs = 580_000, durationMs = null)
        runCurrent()

        assertEquals(emptyList<String>(), prefetched.map { it.item.title })
    }

    @Test
    fun `a podcast enclosure is not worth resolving`() = runTest {
        next = PlayableItem(
            item = item("an episode"),
            handle = PlayHandle.Podcast(),
        )
        prefetcher()
        states.value = playing(positionMs = 580_000, durationMs = 600_000)
        runCurrent()

        assertEquals(emptyList<String>(), prefetched.map { it.item.title })
    }

    /**
     * A torrent is the opposite case, and the reason the video-only rule had to go: its audio-only
     * URL is served by the home server, which has to seek to the file and remux before a single
     * segment exists — ~25 seconds, measured. Left unprepared, that lands as silence at every
     * track change, which is exactly what this class exists to prevent.
     */
    @Test
    fun `a torrent's audio stream is worth preparing`() = runTest {
        next = PlayableItem(
            item = item("S01E02"),
            handle = PlayHandle.Podcast(
                audioUrl = HttpUrl.of("https://home.test/ts/audio/abc/8/index.m3u8"),
            ),
        )
        prefetcher()
        states.value = playing(positionMs = 580_000, durationMs = 600_000)
        runCurrent()

        assertEquals(listOf("S01E02"), prefetched.map { it.item.title })
    }

    /** Already on the device: there is nothing to get ready and nothing to wait for. */
    @Test
    fun `a downloaded copy needs no preparation`() = runTest {
        next = PlayableItem(
            item = item("S01E03"),
            handle = PlayHandle.Podcast(localPath = "/data/S01E03.m4a"),
        )
        prefetcher()
        states.value = playing(positionMs = 580_000, durationMs = 600_000)
        runCurrent()

        assertEquals(emptyList<String>(), prefetched.map { it.item.title })
    }

    private fun playing(id: String = "current", positionMs: Long, durationMs: Long?) =
        PlaybackState(
            itemId = MediaItemId(id),
            title = id,
            artist = null,
            artworkUrl = null,
            isPlaying = true,
            positionMs = positionMs,
            durationMs = durationMs,
            speed = 1f,
        )

    private fun video(title: String) = PlayableItem(
        item = item(title),
        handle = PlayHandle.Video(
            HttpUrl.of("https://www.youtube.com/watch?v=${title.filter(Char::isLetterOrDigit)}"),
        ),
    )

    private fun item(title: String) = MediaItem(
        id = MediaItemId(title),
        sourceId = SourceId("source"),
        title = title,
        publishedAt = null,
        duration = null,
    )
}
