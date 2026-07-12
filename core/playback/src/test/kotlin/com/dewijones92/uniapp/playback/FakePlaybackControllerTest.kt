package com.dewijones92.uniapp.playback

import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.playback.fake.FakePlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakePlaybackControllerTest {

    private val controller = FakePlaybackController()
    private val episode = FakePodcastRepository.sampleEpisode(SourceId("feed-1"))

    @Test
    fun `starts with no state`() {
        assertNull(controller.state.value)
    }

    @Test
    fun `play exposes the item as playing`() {
        controller.play(episode)

        val state = checkNotNull(controller.state.value)
        assertEquals(episode.id, state.itemId)
        assertEquals(episode.title, state.title)
        assertEquals(true, state.isPlaying)
        assertEquals(episode.duration?.inWholeMilliseconds, state.durationMs)
    }

    @Test
    fun `toggle flips playing state`() {
        controller.play(episode)
        controller.togglePlayPause()
        assertEquals(false, controller.state.value?.isPlaying)
        controller.togglePlayPause()
        assertEquals(true, controller.state.value?.isPlaying)
    }
}
