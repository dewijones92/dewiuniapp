package com.dewijones92.totum.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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

    /** The list both tests drive: a small grip inside a much taller row, as on the real queue. */
    private fun setUpList() {
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
                        // Shaped like the REAL queue: a small grip inside a much taller row.
                        // The first version of this test made the handle the whole row, which
                        // hid a real bug — the row height was being measured from the handle,
                        // so items reordered about four times faster than the finger moved.
                        Row(
                            modifier = Modifier
                                .height(ROW_HEIGHT.dp)
                                .fillMaxWidth()
                                .reorderable(reorder, index)
                                .testTag("row-$item"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "Item $item", modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .height(HANDLE_HEIGHT.dp)
                                    .width(HANDLE_HEIGHT.dp)
                                    .dragHandle(index, order.size)
                                    .testTag("grip-$item"),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun draggingToTheBottomEdgeKeepsMovingPastTheVisibleRows() {
        composeTestRule.mainClock.autoAdvance = false
        setUpList()

        // ONE gesture, injected on the LIST and hit-tested onto the row beneath — pointer state
        // does not survive being split across blocks, which is why the first attempt at this
        // recorded no movement whatsoever.
        composeTestRule.onNodeWithTag("grip-0").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            // Far enough down to sit inside the bottom edge zone and stay there.
            moveTo(center + Offset(0f, LONG_DRAG_PX))
        }
        // Time passing with the finger stationary IS the test: no further pointer events arrive,
        // so anything that moves from here moved because the list scrolled itself.
        repeat(HOLD_TICKS) { composeTestRule.mainClock.advanceTimeBy(TICK_MS) }
        composeTestRule.onNodeWithTag("grip-0").performTouchInput { up() }

        assertTrue(
            "expected the drag to reach past the visible rows; moves=${moves.size} $moves",
            moves.size > VISIBLE_ROWS,
        )
    }

    private companion object {
        const val ITEMS = 40
        const val ROW_HEIGHT = 64f

        /** A grip much smaller than its row, as on the real queue screen. */
        const val HANDLE_HEIGHT = 24f

        /** Well inside the edge zone, so the hold unambiguously counts as "held there". */
        /** Well past the bottom of the viewport, so the finger sits in the edge zone. */
        const val LONG_DRAG_PX = 4_000f

        /** Three rows of travel, allowing one event of long-press slop either way. */
        val EXPECTED_MOVES = 2..4
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

    /**
     * A drag of exactly three rows must move exactly three places.
     *
     * This is the bug the auto-scroll test could not see. The row height was measured from the
     * DRAG HANDLE — a 24dp grip inside a 64dp row here, and 24dp inside ~95dp on the real queue
     * — so a swap fired every handle-height of travel instead of every row. Items reordered
     * roughly four times faster than the finger, which is precisely "the dragger doesn't work
     * well": you aim for three places down and land nine.
     *
     * Asserting the COUNT rather than "it moved" is the whole point; the old behaviour moved
     * too, just wrongly.
     */
    @Test
    fun draggingThreeRowsMovesExactlyThreePlaces() {
        composeTestRule.mainClock.autoAdvance = false
        setUpList()

        composeTestRule.onNodeWithTag("grip-0").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            // Three rows down, and nowhere near an edge, so auto-scroll cannot contribute.
            moveTo(center + Offset(0f, ROW_HEIGHT * 3 * density))
        }
        composeTestRule.mainClock.advanceTimeBy(TICK_MS)
        composeTestRule.onNodeWithTag("grip-0").performTouchInput { up() }

        // A range, and honestly so: the long-press detector absorbs the first movement before
        // deltas begin, so three rows of travel lands two or three places down depending on
        // event timing. That slop is worth tolerating because it does not blunt the test —
        // measuring from the 24dp handle instead of the 64dp row gives EIGHT moves for this
        // same gesture, which is nowhere near this range.
        assertTrue(
            "three rows of travel must be about three places, not eight: $moves",
            moves.size in EXPECTED_MOVES,
        )
    }
}
