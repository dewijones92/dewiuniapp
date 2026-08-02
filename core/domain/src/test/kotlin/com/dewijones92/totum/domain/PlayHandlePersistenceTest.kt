package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A handle must survive being written to the queue and read back.
 *
 * Caught on the emulator, not by a unit test: a torrent queued with an audio-only stream played
 * its VIDEO after a restart, because the podcast handle persisted only its local path and the
 * audio URL was silently dropped on the way to the database. Everything above kept working, so
 * nothing failed — it just quietly cost 15.2 MB/min instead of 2.1.
 */
class PlayHandlePersistenceTest {

    private fun roundTrip(handle: PlayHandle): PlayHandle? {
        val (type, value) = handle.persisted()
        return playHandleFrom(type, value)
    }

    @Test
    fun `an audio url survives the round trip`() {
        val audio = HttpUrl.of("https://home.test/ts/audio/abc/7/index.m3u8")
        val restored = roundTrip(PlayHandle.Podcast(audioUrl = audio)) as PlayHandle.Podcast

        assertEquals(audio, restored.audioUrl)
        assertNull(restored.localPath)
    }

    @Test
    fun `a local path survives the round trip`() {
        val restored = roundTrip(PlayHandle.Podcast(localPath = "/data/a.m4a")) as PlayHandle.Podcast

        assertEquals("/data/a.m4a", restored.localPath)
        assertNull(restored.audioUrl)
    }

    @Test
    fun `both survive together`() {
        val audio = HttpUrl.of("https://home.test/ts/audio/abc/7/index.m3u8")
        val restored = roundTrip(
            PlayHandle.Podcast(localPath = "/data/a.m4a", audioUrl = audio),
        ) as PlayHandle.Podcast

        assertEquals("/data/a.m4a", restored.localPath)
        assertEquals(audio, restored.audioUrl)
    }

    /**
     * Rows written before the audio field existed are a BARE path with no prefix. Reading one as
     * anything else would empty the localPath of every already-downloaded queue item and
     * re-fetch the lot.
     */
    @Test
    fun `a legacy bare path still reads as a local path`() {
        val restored = playHandleFrom("PODCAST", "/data/old.m4a") as PlayHandle.Podcast

        assertEquals("/data/old.m4a", restored.localPath)
        assertNull(restored.audioUrl)
    }

    @Test
    fun `an empty handle stays empty`() {
        val restored = roundTrip(PlayHandle.Podcast()) as PlayHandle.Podcast

        assertNull(restored.localPath)
        assertNull(restored.audioUrl)
    }

    /** The other handles are untouched by any of this and must stay exactly as they were. */
    @Test
    fun `video handles round trip unchanged`() {
        val url = HttpUrl.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertEquals(PlayHandle.Video(url), roundTrip(PlayHandle.Video(url)))
        assertEquals(PlayHandle.LocalVideo("/data/v.mkv"), roundTrip(PlayHandle.LocalVideo("/data/v.mkv")))
    }
}
