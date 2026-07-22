package com.dewijones92.uniapp.notifications

import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class NewUploadsTrackerTest {

    private val tracker = InMemoryNewUploadsTracker()

    private fun video(id: String) =
        FeedVideo(id, "title", null, null, null, checkNotNull(FeedVideo.watchUrlFor(id)))

    @Test
    fun `first look bootstraps - nothing is new`() {
        val feed = listOf(video("a"), video("b"))
        assertEquals(emptyList<FeedVideo>(), tracker.newUploads(feed))
    }

    @Test
    fun `later uploads not in the seen set are new`() {
        tracker.newUploads(listOf(video("a"), video("b"))) // bootstrap
        val newer = tracker.newUploads(listOf(video("c"), video("a"), video("b")))
        assertEquals(listOf("c"), newer.map { it.videoId })
    }

    @Test
    fun `marking seen clears them`() {
        tracker.newUploads(listOf(video("a"))) // bootstrap
        val feed = listOf(video("c"), video("a"))
        assertEquals(listOf("c"), tracker.newUploads(feed).map { it.videoId })
        tracker.markSeen(feed)
        assertEquals(emptyList<FeedVideo>(), tracker.newUploads(feed))
    }
}
