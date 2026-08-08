package com.dewijones92.totum.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.playback.PlaybackState
import com.dewijones92.totum.theme.TotumTheme
import com.dewijones92.totum.ui.player.ChosenBrightness
import com.dewijones92.totum.ui.player.VideoSettings
import com.dewijones92.totum.ui.player.VideoStageWithControls
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A brightness set by gesture survives going fullscreen — and coming back out.
 *
 * Dewi, 2026-08-08: *"The brightness is turned up when I go into a video item, but then it's turned
 * down when I go into full screen video."* He had set a level by swipe earlier in the session, and
 * confirmed it is the **backlight** that changes and that it never comes back on leaving fullscreen.
 *
 * **Why this has to be an instrumented test and could not be a unit one.** The defect is not in any
 * decision the code makes — every individual step is right. It is in the ORDER Compose runs them.
 * Entering fullscreen swaps one whole subtree for another (`FullPlayer.kt`: fullscreen renders the
 * stage directly, windowed renders it deep inside the draggable content), so the outgoing stage is
 * disposed and a fresh one is created. The new one re-applied the remembered brightness during
 * *composition*; the old one released the window override in its `onDispose`, which runs in the
 * *effects* phase afterwards. Later wins, so the override was wiped at every transition and the
 * screen dropped back to system brightness for the rest of the session. Nothing about that is
 * visible in a `ShortArray`, a view model, or any single function — only in a real composition.
 *
 * So the test drives the real `VideoStageWithControls` through the real subtree swap and asks the
 * real window what brightness it is showing.
 */
@RunWith(AndroidJUnit4::class)
class BrightnessSurvivesFullscreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExoPlayer

    private val state = PlaybackState(
        itemId = MediaItemId("abc123"),
        title = "Is this Gary Stevensons last EVER interview?",
        artist = "Novara Media",
        artworkUrl = null,
        kind = MediaKind.VIDEO,
        // Deliberately paused: playing would start the controls auto-hide timer, which has nothing
        // to do with this and only adds a way for the test to become flaky.
        isPlaying = false,
        positionMs = 60_000,
        durationMs = 2_520_000,
        speed = 1f,
        hasVideo = true,
    )

    @Before
    fun setUp() {
        composeTestRule.runOnUiThread {
            player = ExoPlayer.Builder(composeTestRule.activity).build()
        }
    }

    @After
    fun tearDown() {
        composeTestRule.runOnUiThread { player.release() }
        ChosenBrightness.forget()
    }

    /** What the window is actually showing, which is the only thing the user can see. */
    private fun windowBrightness(): Float =
        composeTestRule.activity.window.attributes.screenBrightness

    /**
     * Mirrors `FullPlayer`: fullscreen and windowed are two different subtrees, so toggling really
     * does dispose one stage and create another. The extra [Box] is not decoration — windowed, the
     * stage sits nested inside the draggable content, and it is that difference in position that
     * makes Compose treat them as different composables rather than reusing one.
     */
    @Composable
    private fun Harness(fullscreen: Boolean, player: Player) {
        TotumTheme {
            if (fullscreen) {
                Stage(player, fullscreen = true)
            } else {
                Box { Stage(player, fullscreen = false) }
            }
        }
    }

    @Composable
    private fun Stage(player: Player, fullscreen: Boolean) {
        VideoStageWithControls(
            state = state,
            player = player,
            settings = VideoSettings.None,
            fullscreen = fullscreen,
            onToggleFullscreen = {},
            onDismiss = {},
            onTogglePlayPause = {},
            onSeekTo = {},
            onSeekBackward = {},
            onSeekForward = {},
        )
    }

    /** THE BUG. Going fullscreen must not throw away the brightness you set. */
    @Test
    fun theChosenBrightnessSurvivesEnteringFullscreen() {
        ChosenBrightness.choose(CHOSEN)
        var fullscreen by mutableStateOf(false)
        composeTestRule.setContent { Harness(fullscreen, player) }
        composeTestRule.waitForIdle()
        assertEquals("the windowed stage should already be showing it", CHOSEN, windowBrightness(), TOLERANCE)

        fullscreen = true
        composeTestRule.waitForIdle()

        assertEquals(
            "fullscreen dropped the brightness back to the system's — the screen visibly dims",
            CHOSEN,
            windowBrightness(),
            TOLERANCE,
        )
    }

    /** And coming back out must not throw it away either — Dewi: *"it stays dim"*. */
    @Test
    fun theChosenBrightnessSurvivesLeavingFullscreenAgain() {
        ChosenBrightness.choose(CHOSEN)
        var fullscreen by mutableStateOf(true)
        composeTestRule.setContent { Harness(fullscreen, player) }
        composeTestRule.waitForIdle()

        fullscreen = false
        composeTestRule.waitForIdle()

        assertEquals(CHOSEN, windowBrightness(), TOLERANCE)
    }

    /** Several times over, since a queue of videos means many transitions in a sitting. */
    @Test
    fun itSurvivesRepeatedToggling() {
        ChosenBrightness.choose(CHOSEN)
        var fullscreen by mutableStateOf(false)
        composeTestRule.setContent { Harness(fullscreen, player) }

        repeat(TOGGLES) {
            fullscreen = !fullscreen
            composeTestRule.waitForIdle()
            assertEquals("lost after ${it + 1} toggles", CHOSEN, windowBrightness(), TOLERANCE)
        }
    }

    /**
     * The other half of the contract, and the reason the release exists at all: when the video goes
     * away entirely the window must go back to the system's brightness, or the queue and settings
     * screens inherit a dimmed window. A fix that simply stopped releasing would pass every test
     * above and break this one.
     */
    @Test
    fun theOverrideIsReleasedWhenTheVideoGoesAwayCompletely() {
        ChosenBrightness.choose(CHOSEN)
        var showing by mutableStateOf(true)
        composeTestRule.setContent { if (showing) Harness(fullscreen = false, player = player) else TotumTheme {} }
        composeTestRule.waitForIdle()
        assertEquals(CHOSEN, windowBrightness(), TOLERANCE)

        showing = false
        composeTestRule.waitForIdle()

        assertEquals(
            "with no video on screen the window must follow the system again",
            FOLLOW_SYSTEM,
            windowBrightness(),
            TOLERANCE,
        )
    }

    /** With nothing chosen, the app must not touch the window at all. */
    @Test
    fun withNoChoiceTheWindowIsLeftAlone() {
        var fullscreen by mutableStateOf(false)
        composeTestRule.setContent { Harness(fullscreen, player) }
        composeTestRule.waitForIdle()

        fullscreen = true
        composeTestRule.waitForIdle()

        assertEquals(FOLLOW_SYSTEM, windowBrightness(), TOLERANCE)
    }

    private companion object {
        /** Distinct from 0, 1 and 0.5, so a passing test cannot be a coincidence of defaults. */
        const val CHOSEN = 0.87f
        const val FOLLOW_SYSTEM = -1f
        const val TOLERANCE = 0.001f
        const val TOGGLES = 6
    }
}
