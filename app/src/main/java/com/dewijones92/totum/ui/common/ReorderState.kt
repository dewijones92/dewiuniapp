package com.dewijones92.totum.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs

/**
 * Long-press-and-drag reordering for a list.
 *
 * Hand-rolled rather than pulling in a dependency, and deliberately simple: instead of
 * mapping pointer positions onto item bounds, it accumulates the drag and swaps one
 * position each time the accumulated distance passes a row's height. That reads
 * identically to the user, survives lists whose lazy indices don't line up with the
 * data (this one interleaves group headers), and has no measurement edge cases.
 *
 * [onMove] is called as the drag crosses each boundary, so the list reorders live and
 * the underlying store stays the single source of truth — nothing to commit on release.
 */
class ReorderState internal constructor(
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    internal var draggingIndex by mutableIntStateOf(NONE)
        private set
    private var accumulated by mutableFloatStateOf(0f)
    private var rowHeight by mutableStateOf(0)

    /** True while [index] is the row being dragged. */
    fun isDragging(index: Int): Boolean = draggingIndex == index

    /** How far to visually shift the dragged row. */
    fun offsetFor(index: Int): Float = if (isDragging(index)) accumulated else 0f

    /**
     * Attach to a row's grip to make it draggable. [index] is the row's index **in the
     * data**, which is what [onMove] receives.
     */
    fun Modifier.dragHandle(index: Int, itemCount: Int): Modifier = this
        .onSizeChanged { if (it.height > 0) rowHeight = it.height }
        .pointerInput(index, itemCount) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    draggingIndex = index
                    accumulated = 0f
                },
                onDragEnd = { reset() },
                onDragCancel = { reset() },
                onDrag = { _, delta ->
                    accumulated += delta.y
                    val step = rowHeight.takeIf { it > 0 } ?: return@detectDragGesturesAfterLongPress
                    while (abs(accumulated) >= step) {
                        val direction = if (accumulated > 0) 1 else -1
                        val from = draggingIndex
                        val to = from + direction
                        if (to !in 0 until itemCount) {
                            // At an end: stop accumulating so the row doesn't drift away.
                            accumulated = 0f
                            break
                        }
                        onMove(from, to)
                        draggingIndex = to
                        accumulated -= direction * step
                    }
                },
            )
        }

    private fun reset() {
        draggingIndex = NONE
        accumulated = 0f
    }

    private companion object {
        const val NONE = -1
    }
}

/** Remembers a [ReorderState] that reports moves to [onMove]. */
@Composable
fun rememberReorderState(onMove: (from: Int, to: Int) -> Unit): ReorderState =
    remember(onMove) { ReorderState(onMove) }

/** Lifts the dragged row above its neighbours and follows the finger. */
fun Modifier.reorderable(state: ReorderState, index: Int): Modifier = this.graphicsLayer {
    translationY = state.offsetFor(index)
    // A little lift so it's obvious which row you picked up.
    shadowElevation = if (state.isDragging(index)) DRAG_ELEVATION else 0f
    scaleX = if (state.isDragging(index)) DRAG_SCALE else 1f
    scaleY = scaleX
}

private const val DRAG_ELEVATION = 12f
private const val DRAG_SCALE = 1.02f
