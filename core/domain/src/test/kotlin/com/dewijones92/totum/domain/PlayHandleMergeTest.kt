package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Merging routes to the same thing. The rule is one-directional on purpose: re-queueing may ADD
 * a way to reach an item and may never take one away.
 *
 * The naive alternative — newer wins — is what these tests exist to prevent. It silently drops a
 * `localPath` whenever a fresh copy arrives without one, and the app then streams a file already
 * sitting on the disk: no error, no log, just data spent on nothing.
 */
class PlayHandleMergeTest {

    private val audio = HttpUrl.of("https://home.test/ts/audio/abc/7/index.m3u8")
    private val watch = HttpUrl.of("https://youtube.test/watch?v=aaaaaaaaaaa")

    @Test
    fun `an audio-only route is adopted`() {
        val merged = PlayHandle.Podcast().mergedWith(PlayHandle.Podcast(audioUrl = audio))

        assertEquals(PlayHandle.Podcast(audioUrl = audio), merged)
    }

    /** The regression that started this: a fresh handle must not cost us the downloaded file. */
    @Test
    fun `a newer handle without a local path does not lose the one we have`() {
        val downloaded = PlayHandle.Podcast(localPath = "/data/S01E01.m4a")

        val merged = downloaded.mergedWith(PlayHandle.Podcast(audioUrl = audio))

        assertEquals(PlayHandle.Podcast(localPath = "/data/S01E01.m4a", audioUrl = audio), merged)
    }

    @Test
    fun `both routes survive when only one side knows each`() {
        val merged = PlayHandle.Podcast(audioUrl = audio)
            .mergedWith(PlayHandle.Podcast(localPath = "/data/a.m4a"))

        assertEquals(PlayHandle.Podcast(localPath = "/data/a.m4a", audioUrl = audio), merged)
    }

    @Test
    fun `a newer local path wins over an older one`() {
        val merged = PlayHandle.Podcast(localPath = "/old/a.m4a")
            .mergedWith(PlayHandle.Podcast(localPath = "/new/a.m4a"))

        assertEquals("/new/a.m4a", (merged as PlayHandle.Podcast).localPath)
    }

    /**
     * A video is not a fuller version of a podcast, so there is nothing to combine — the newer
     * handle simply replaces it. Merging across pillars would invent a handle that is neither.
     */
    @Test
    fun `different pillars do not merge`() {
        val merged = PlayHandle.Podcast(localPath = "/data/a.m4a").mergedWith(PlayHandle.Video(watch))

        assertEquals(PlayHandle.Video(watch), merged)
    }

    @Test
    fun `merging with an identical handle changes nothing`() {
        val handle = PlayHandle.Podcast(localPath = "/data/a.m4a", audioUrl = audio)

        assertEquals(handle, handle.mergedWith(handle))
    }
}
