package com.dewijones92.totum.playback

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dewijones92.totum.MainActivity
import com.dewijones92.totum.TotumApplication
import com.dewijones92.totum.common.Breadcrumbs
import com.dewijones92.totum.common.HttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The preload nomination has to cross a process-shaped boundary, and only a device can show it.
 *
 * The app cannot preload anything itself: only the SERVICE owns media sources, and a
 * `MediaController` cannot be handed one. So the app names what is coming over a custom session
 * command and the service builds it. That handoff is the whole new mechanism, and it is exactly the
 * kind of thing that compiles perfectly and silently does nothing — a command the session never
 * advertised is rejected without a word.
 *
 * Asserting the service's own breadcrumb is what makes this real: it is written after
 * `DefaultPreloadManager` has accepted the item, so the line existing means the command arrived,
 * was permitted, and the manager took it.
 */
class PreloadCommandReachesServiceTest {

    /** Foreground, or the session may not be connected at all. */
    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as TotumApplication
    private val controller get() = (app.container).playbackController

    @Before
    fun waitForSession() = runBlocking(Dispatchers.Main) {
        val connected = withTimeoutOrNull(TIMEOUT_MS) {
            while (controller.player == null) delay(POLL_MS)
            true
        }
        assertEquals("the media controller never connected to the playback service", true, connected)
        Breadcrumbs.clear()
    }

    @Test
    fun `nominating the next item reaches the service and is accepted`() = runBlocking(Dispatchers.Main) {
        controller.preloadNext(HttpUrl.of("https://example.test/next-episode.mp3"))

        val held = withTimeoutOrNull(TIMEOUT_MS) {
            while (Breadcrumbs.snapshot().none { "holding the first" in it.message }) delay(POLL_MS)
            true
        } ?: false

        assertTrue(
            "the preload command never reached the service. A session command that was not " +
                "advertised in onConnect is rejected silently, which looks exactly like this. " +
                "Trail: " + Breadcrumbs.snapshot().map { it.message }.takeLast(TRAIL_LINES),
            held,
        )
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val POLL_MS = 200L
        const val TRAIL_LINES = 6
    }
}
