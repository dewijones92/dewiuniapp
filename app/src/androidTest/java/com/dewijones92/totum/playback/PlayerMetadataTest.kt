package com.dewijones92.totum.playback

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dewijones92.totum.MainActivity
import com.dewijones92.totum.TotumApplication
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The view count and publication date reaching the player, across the session boundary.
 *
 * Dewi, 2026-08-06: *"this additional detail must appear within video page also"*. Getting it there
 * is not a UI change: the app and the player live either side of a `MediaSession`, and only a small
 * fixed set of fields crosses it. There is no `MediaMetadata` field for a view count or a relative
 * publication date, so both ride in the metadata **extras** — and a Bundle key that is written but
 * never read, or read under a different name, compiles perfectly and delivers nothing at all.
 *
 * That failure mode is not hypothetical in this repo: the preload command was silently rejected for
 * exactly this shape of reason, and a whole test exists for it (`PreloadCommandReachesServiceTest`).
 * So this asserts the values come back out of the real session rather than trusting the round-trip.
 *
 * The URI is deliberately unreachable. Nothing here is about playing bytes — `setMediaItem` publishes
 * the metadata regardless of whether a single one ever arrives.
 */
class PlayerMetadataTest {

    /** Foreground, or the session may not be connected at all. */
    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as TotumApplication
    private val container get() = app.container
    private val controller get() = container.playbackController
    private val queue get() = container.playbackQueue

    private val publishedAt = Instant.parse("2026-08-01T09:00:00Z")

    @Before
    fun waitForSession() = runBlocking(Dispatchers.Main) {
        val connected = withTimeoutOrNull(TIMEOUT_MS) {
            while (controller.player == null) delay(POLL_MS)
            true
        }
        assertEquals("the media controller never connected to the playback service", true, connected)
        queue.clear()
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.Main) {
            queue.clear()
            controller.player?.stop()
            controller.player?.clearMediaItems()
        }
    }

    @Test
    fun `the view count and both date forms survive the session`() = runBlocking(Dispatchers.Main) {
        val id = "metadata-with-facts"
        controller.play(itemWithMetadata(id))

        val state = awaitStateFor(id)
        assertTrue("the player never reported the item as current", state != null)

        assertEquals("the view count did not cross the session", "1.2M views", state!!.viewsText)
        assertEquals("the relative date did not cross the session", "5 days ago", state.publishedText)
        assertEquals("the absolute date did not cross the session", publishedAt, state.publishedAt)
    }

    /**
     * And absence stays absence.
     *
     * A Bundle cannot hold a null Long, so the publication instant travels as epoch millis with a
     * sentinel for "there wasn't one" — which is precisely the shape that turns a missing date into a
     * confident wrong one (1970, or 1ms after the epoch) if the sentinel is mishandled.
     */
    @Test
    fun `an item with no views or date reports neither rather than a placeholder`() =
        runBlocking(Dispatchers.Main) {
            val id = "metadata-with-nothing"
            controller.play(itemWithMetadata(id, withMetadata = false))

            val state = awaitStateFor(id)
            assertTrue(state != null)
            assertEquals("a view count was invented", null, state!!.viewsText)
            assertEquals("a relative date was invented", null, state.publishedText)
            assertEquals("an epoch date was invented from the absent sentinel", null, state.publishedAt)
        }

    /**
     * Through the QUEUE, which is how it actually happens.
     *
     * The queue is what holds the listing, and `PlaybackQueue.route` is the only thing that starts
     * playback — so this covers the path a tap takes, not just a direct call.
     */
    @Test
    fun `playing from the queue carries the listing facts too`() = runBlocking(Dispatchers.Main) {
        val id = "metadata-via-queue"
        queue.playNow(PlayableItem(itemWithMetadata(id), PlayHandle.Podcast()))

        val state = awaitStateFor(id)
        assertEquals("the queue's own play path dropped the view count", "1.2M views", state?.viewsText)
        assertEquals("5 days ago", state?.publishedText)
    }

    /**
     * Waits for the player's state to be about [id] specifically.
     *
     * A per-test id, and matched here, because the service outlives a single test and a StateFlow
     * keeps its last value — so a loop that waited for "any state" would be handed the PREVIOUS
     * test's, complete with its view count, and pass while proving nothing.
     */
    private suspend fun awaitStateFor(id: String): PlaybackState? = withTimeoutOrNull(TIMEOUT_MS) {
        while (controller.state.value?.itemId?.value != id) delay(POLL_MS)
        controller.state.value
    }

    private fun itemWithMetadata(id: String, withMetadata: Boolean = true) = MediaItem(
        id = MediaItemId(id),
        sourceId = SourceId("test"),
        title = "an item with facts about it",
        publishedAt = publishedAt.takeIf { withMetadata },
        publishedText = "5 days ago".takeIf { withMetadata },
        duration = null,
        author = "Novara Media",
        mediaUrl = HttpUrl.of("https://example.test/episode.mp3"),
        viewsText = "1.2M views".takeIf { withMetadata },
    )

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val POLL_MS = 150L
    }
}
