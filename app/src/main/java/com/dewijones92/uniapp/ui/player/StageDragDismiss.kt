package com.dewijones92.uniapp.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Drag-the-stage-down-to-dismiss gesture for the full player: put [handle] on
 * the stage (the video/artwork) and [contentOffset] on the content that should
 * follow the finger. Dragging the handle down past [DISMISS_THRESHOLD] calls
 * `onDismiss` (dropping to the still-playing mini player); a shorter drag
 * springs back. Only the handle drives it, so scrolling the details below is
 * untouched.
 */
internal class StageDragDismiss(val handle: Modifier, val contentOffset: Modifier)

@Composable
internal fun rememberStageDragDismiss(onDismiss: () -> Unit): StageDragDismiss {
    val thresholdPx = with(LocalDensity.current) { DISMISS_THRESHOLD.toPx() }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val handle = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, delta ->
                change.consume()
                scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
            },
            onDragEnd = {
                if (offsetY.value > thresholdPx) onDismiss() else scope.launch { offsetY.animateTo(0f) }
            },
            onDragCancel = { scope.launch { offsetY.animateTo(0f) } },
        )
    }
    val contentOffset = Modifier.offset { IntOffset(0, offsetY.value.roundToInt()) }
    return StageDragDismiss(handle, contentOffset)
}

/** Drag the stage down past this far to drop to the mini player. */
private val DISMISS_THRESHOLD = 160.dp
