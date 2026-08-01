package com.dewijones92.totum.ui.common

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic that decides when a drag becomes a move.
 *
 * Worth testing apart from the gesture because it is where the off-by-ones live — the ends of
 * the list especially — and because auto-scroll feeds this same function. A scroll of N pixels
 * and a finger travelling N pixels are the same event to a list, and this is where that claim
 * is either true or not.
 */
class ReorderStateTest {

    private val moves = mutableListOf<Pair<Int, Int>>()

    private fun state(startIndex: Int, count: Int, rowHeight: Int = 100) =
        ReorderState({ from, to -> moves += from to to }, LazyListState(), TestScope()).apply {
            this.rowHeight = rowHeight
            this.itemCount = count
            this.draggingIndex = startIndex
        }

    @Test
    fun `a drag shorter than a row moves nothing`() {
        val reorder = state(startIndex = 2, count = 10)

        reorder.applyDrag(60f)

        assertEquals(emptyList<Pair<Int, Int>>(), moves)
        assertEquals(2, reorder.draggingIndex)
    }

    @Test
    fun `crossing one row swaps once`() {
        val reorder = state(startIndex = 2, count = 10)

        reorder.applyDrag(100f)

        assertEquals(listOf(2 to 3), moves)
        assertEquals(3, reorder.draggingIndex)
    }

    /**
     * The case auto-scroll depends on: a single large travel must move several positions, not
     * one. Scrolling a long way in one tick, or a fast flick of the finger, both arrive here.
     */
    @Test
    fun `one large travel moves several positions`() {
        val reorder = state(startIndex = 0, count = 10)

        reorder.applyDrag(350f)

        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3), moves)
        assertEquals(3, reorder.draggingIndex)
    }

    @Test
    fun `dragging upwards moves the other way`() {
        val reorder = state(startIndex = 5, count = 10)

        reorder.applyDrag(-250f)

        assertEquals(listOf(5 to 4, 4 to 3), moves)
        assertEquals(3, reorder.draggingIndex)
    }

    /**
     * Held against the end of the list, it must stop rather than keep accumulating — otherwise
     * the row drifts off under the finger and comes back only after an equal drag the other way,
     * which feels broken. This matters far more now that auto-scroll can hold a row at the
     * bottom edge indefinitely.
     */
    @Test
    fun `it stops at the bottom instead of drifting`() {
        val reorder = state(startIndex = 8, count = 10)

        reorder.applyDrag(1_000f)

        assertEquals(listOf(8 to 9), moves)
        assertEquals(9, reorder.draggingIndex)
        assertEquals("no leftover travel, or the row drifts", 0f, reorder.offsetFor(9), 0.01f)
    }

    @Test
    fun `it stops at the top instead of drifting`() {
        val reorder = state(startIndex = 1, count = 10)

        reorder.applyDrag(-1_000f)

        assertEquals(listOf(1 to 0), moves)
        assertEquals(0, reorder.draggingIndex)
        assertEquals(0f, reorder.offsetFor(0), 0.01f)
    }

    /** Before the row has been measured there is no step size, so nothing can be decided yet. */
    @Test
    fun `an unmeasured row moves nothing`() {
        val reorder = state(startIndex = 2, count = 10, rowHeight = 0)

        reorder.applyDrag(500f)

        assertEquals(emptyList<Pair<Int, Int>>(), moves)
    }
}
