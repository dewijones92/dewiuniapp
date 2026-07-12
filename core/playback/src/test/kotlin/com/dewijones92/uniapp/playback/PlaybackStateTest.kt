package com.dewijones92.uniapp.playback

import com.dewijones92.uniapp.domain.MediaItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackStateTest {

    private fun state(positionMs: Long = 0, durationMs: Long? = null) = PlaybackState(
        itemId = MediaItemId("item"),
        title = "Title",
        isPlaying = true,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    @Test
    fun `progress is a clamped fraction when duration is known`() {
        assertEquals(0.5f, state(positionMs = 30_000, durationMs = 60_000).progress)
        assertEquals(1.0f, state(positionMs = 90_000, durationMs = 60_000).progress)
    }

    @Test
    fun `progress is null when duration is unknown`() {
        assertNull(state(positionMs = 30_000).progress)
    }

    @Test
    fun `invalid values are unrepresentable`() {
        assertThrows(IllegalArgumentException::class.java) { state(positionMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { state(durationMs = 0) }
    }
}
