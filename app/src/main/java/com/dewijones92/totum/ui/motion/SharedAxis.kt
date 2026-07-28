package com.dewijones92.totum.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Horizontal shared-axis transition: the outgoing screen leaves the way you came from and the
 * incoming one arrives from the other side, both fading.
 *
 * Replaces the default cross-fade-and-scale for the bottom tabs. Tabs sit in a row, so moving
 * right should look like moving right — a fade alone tells you something changed but not which
 * way you went, and the scale reads as a dialog opening rather than lateral travel.
 *
 * The travel is deliberately small: a third of the width would read as a page swipe, implying
 * the tabs can be swiped between. They cannot, so the motion hints at direction without
 * promising a gesture it will not honour.
 *
 * Its own file because it is shared design vocabulary — the next screen that needs lateral
 * motion should use this rather than hand-rolling a second interpretation of the same idea.
 */
fun sharedXAxis(forward: Boolean): ContentTransform {
    val enter = fadeIn(tween(MOTION_MS)) + slideInHorizontally(tween(MOTION_MS)) { width ->
        if (forward) width / TRAVEL_FRACTION else -width / TRAVEL_FRACTION
    }
    // Out faster than in, so the arriving screen is never competing with the leaving one.
    val exit = fadeOut(tween(MOTION_MS / 2)) + slideOutHorizontally(tween(MOTION_MS)) { width ->
        if (forward) -width / TRAVEL_FRACTION else width / TRAVEL_FRACTION
    }
    return enter togetherWith exit
}

private const val MOTION_MS = 260
private const val TRAVEL_FRACTION = 6
