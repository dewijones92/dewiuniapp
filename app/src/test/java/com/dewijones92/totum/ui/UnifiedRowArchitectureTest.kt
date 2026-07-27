package com.dewijones92.totum.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Guards the rule that keeps rows unified: **an engine's wire type must not reach a
 * composable.**
 *
 * This is not a style preference. `RelatedVideos` rendered InnerTube's `FeedVideo`
 * directly, so the shared `MediaItemRow` — which takes a `MediaItem` — could not be used,
 * and a bespoke row was written instead. That row silently had no long-press menu, no
 * play state, no offline indicator and no pillar label, on the one screen you reach by
 * watching something. Nothing failed; it just quietly offered less.
 *
 * Converting at the view-model boundary (as every other feed screen already did) is what
 * fixes it, and this is what stops the next surface skipping that step.
 */
class UnifiedRowArchitectureTest {

    private val wireType = "com.dewijones92.totum.innertube.feeds.FeedVideo"

    @Test
    fun `no composable imports an engine wire type`() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                "@Composable" in text && "import $wireType" in text
            }
            .map { it.name }
            .toList()

        assertEquals(
            "These files render UI and import $wireType. Map it to MediaItem in the " +
                "view model instead, so the shared MediaItemRow (and everything it " +
                "provides) applies. Offenders:",
            emptyList<String>(),
            offenders,
        )
    }

    /** The test is worthless if it is looking at nothing, which a path change would cause. */
    @Test
    fun `the source tree is actually being scanned`() {
        val composables = File("src/main/java").walkTopDown()
            .count { it.isFile && it.extension == "kt" && "@Composable" in it.readText() }

        assert(composables > 20) { "expected to find the app's composables, found $composables" }
    }
}
