package com.dewijones92.uniapp.domain

import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class ModelInvariantsTest {

    @Test
    fun `source id must not be blank`() {
        assertThrows(IllegalArgumentException::class.java) { SourceId(" ") }
    }

    @Test
    fun `media item id must not be blank`() {
        assertThrows(IllegalArgumentException::class.java) { MediaItemId("") }
    }

    @Test
    fun `media item duration must be positive when present`() {
        assertThrows(IllegalArgumentException::class.java) {
            mediaItem(duration = (-1).seconds)
        }
        assertThrows(IllegalArgumentException::class.java) {
            mediaItem(duration = 0.seconds)
        }
    }

    @Test
    fun `media item allows unknown duration`() {
        mediaItem(duration = null)
    }

    private fun mediaItem(duration: kotlin.time.Duration?) = MediaItem(
        id = MediaItemId("item-1"),
        sourceId = SourceId("source-1"),
        title = "Title",
        publishedAt = Instant.parse("2026-07-01T00:00:00Z"),
        duration = duration,
    )
}
