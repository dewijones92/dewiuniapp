package com.dewijones92.totum.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.dewijones92.totum.ui.common.rememberReorderState
import com.dewijones92.totum.ui.common.reorderable
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Dragging an item further than the screen, which is the whole point of the feature.
 *
 * Dewi, 2026-08-01: *"able to drag and move while scrolling?? move big distances etc"*. His
 * queue is 74 items; before auto-scroll a drag could not move anything past the bottom of the
 * viewport, so "send this to the end" was simply not expressible as a gesture however patient
 * you were.
 *
 * Instrumented rather than a unit test because the thing under test is the WIRING: the swap
 * arithmetic is covered on the JVM by `ReorderStateTest`, and it stayed correct throughout —
 * what was missing was anything calling it while the finger sat still at an edge. A finger held
 * perfectly still emits no pointer events at all, so nothing but a real gesture on a real list
 * proves the scroll loop runs.
 *
 * The assertion is deliberately "more than a screenful" rather than an exact index: the precise
 * number depends on scroll timing, and pinning it would make the test fragile about the one
 * thing that does not matter. What matters is that the reach is no longer capped by the display.
 */
class ReorderAutoScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val moves = mutableListOf<Pair<Int, Int>>()

    @Test
    fun draggingToTheBottomEdgeKeepsMovingPastTheVisibleRows() {
        composeTestRule.setContent {
            val order = remember { mutableStateListOf<Int>().apply { addAll(0 until ITEMS) } }
            val listState = rememberLazyListState()
            val reorder = rememberReorderState(listState) { from, to ->
                moves += from to to
                order.add(to, order.removeAt(from))
            }
            LazyColumn(
                state = listState,
                modifier = with(reorder) { Modifier.fillMaxSize().testTag(LIST).reorderContainer() },
            ) {
                itemsIndexed(order, key = { _, item -> item }) { index, item ->
                    with(reorder) {
                        Text(
                            text = "Item $item",
                            modifier = Modifier
                                .height(ROW_HEIGHT.dp)
                                .fillMaxWidth()
                                .reorderable(reorder, index)
                                .dragHandle(index, order.size)
                                .testTag("row-$item"),
                        )
                    }
                }
            }
        }

        // The clock is driven by hand: while a row is held at an edge the scroll loop never
        // idles, so anything that waits for quiescence (the default) would hang rather than fail.
        composeTestRule.mainClock.autoAdvance = false

        // ONE gesture, injected on the LIST and hit-tested onto the row beneath — pointer state
        // does not survive being split across blocks, which is why the first attempt at this
        // recorded no movement whatsoever.
        composeTestRule.onNodeWithTag(LIST).performTouchInput {
            down(Offset(centerX, ROW_HEIGHT / 2f))
            advanceEventTime(LONG_PRESS_MS)
            moveTo(Offset(centerX, height - EDGE_MARGIN))
        }
        // Time passing with the finger stationary IS the test: no further pointer events arrive,
        // so anything that moves from here moved because the list scrolled itself.
        repeat(HOLD_TICKS) { composeTestRule.mainClock.advanceTimeBy(TICK_MS) }
        composeTestRule.onNodeWithTag(LIST).performTouchInput { up() }

        assertTrue(
            "expected the drag to reach past the visible rows; moves=${moves.size} $moves",
            moves.size > VISIBLE_ROWS,
        )
    }

    private companion object {
        const val ITEMS = 40
        const val ROW_HEIGHT = 64f

        /** Well inside the edge zone, so the hold unambiguously counts as "held there". */
        const val EDGE_MARGIN = 20f
        const val LIST = "list"

        /** Comfortably past Compose's long-press threshold. */
        const val LONG_PRESS_MS = 1_000L
        const val HOLD_TICKS = 40
        const val TICK_MS = 16L

        /**
         * A generous upper bound on what fits on screen at [ROW_HEIGHT]dp — the point is to
         * assert the drag went FURTHER than the display allows, without depending on the exact
         * size of whatever device this runs on.
         */
        const val VISIBLE_ROWS = 15
    }
}
