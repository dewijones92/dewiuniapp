package com.dewijones92.uniapp.domain

import com.dewijones92.uniapp.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStateTest {

    @Test
    fun `fraction is the position over the duration`() {
        assertEquals(0.25f, PlayState.InProgress(15_000, 60_000).fraction)
    }

    /** A live stream or a feed with no declared length must not render a bogus sliver. */
    @Test
    fun `fraction is unknown without a duration`() {
        assertNull(PlayState.InProgress(15_000, null).fraction)
        assertNull(PlayState.InProgress(15_000, 0).fraction)
    }

    /**
     * Media3 can report a position slightly past the declared duration; a fraction above
     * 1 would overflow the progress sliver rather than fill it.
     */
    @Test
    fun `fraction never exceeds one`() {
        assertEquals(1f, PlayState.InProgress(70_000, 60_000).fraction)
    }

    @Test
    fun `only Played counts as played`() {
        assertTrue(PlayState.Played.isPlayed)
        assertFalse(PlayState.Unplayed.isPlayed)
        assertFalse(PlayState.InProgress(1, 2).isPlayed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative position is not representable`() {
        PlayState.InProgress(-1, 60_000)
    }

    @Test
    fun `a handle knows its pillar, so mixed lists never guess from a URL`() {
        val url = requireNotNull(HttpUrl.parse("https://youtube.com/watch?v=abc"))

        assertEquals(MediaKind.VIDEO, PlayHandle.Video(url).pillar)
        assertEquals(MediaKind.VIDEO, PlayHandle.LocalVideo("/tmp/a.mkv").pillar)
        assertEquals(MediaKind.PODCAST, PlayHandle.Podcast().pillar)
        assertEquals(MediaKind.PODCAST, PlayHandle.Podcast("/tmp/a.mp3").pillar)
    }
}
