package com.dewijones92.totum.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A sweeping highlight over placeholder shapes, for while a feed is loading.
 *
 * Replaces a centred spinner. A spinner says "wait" and nothing else; a skeleton says what is
 * coming and roughly how much of it, so the screen feels like it is filling in rather than
 * blocked. It also stops the layout jumping when content lands, because the placeholders are
 * the shape of the rows that replace them.
 *
 * The sweep is a translated gradient masked to the content with [BlendMode.SrcIn], so it lights
 * up whatever shapes are drawn inside it without needing to know what they are — one modifier,
 * any skeleton.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS), RepeatMode.Restart),
        label = "sweep",
    )
    val highlight = MaterialTheme.colorScheme.surfaceBright

    return this
        // Offscreen compositing so the mask applies to the whole skeleton at once rather than
        // per shape, which is what makes it read as one sheet of light passing over.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val travel = size.width * SWEEP_TRAVEL
            val start = -travel + sweep * (size.width + 2 * travel)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + travel, size.height),
                ),
                blendMode = BlendMode.SrcIn,
            )
        }
}

/** One placeholder row shaped like a media row: thumbnail, two lines of text. */
@Composable
fun MediaRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Block(Modifier.size(width = THUMB_WIDTH, height = THUMB_HEIGHT))
        Column(Modifier.padding(start = 12.dp)) {
            Block(Modifier.fillMaxWidth(TITLE_FRACTION).height(LINE))
            Spacer(Modifier.height(8.dp))
            Block(Modifier.fillMaxWidth(SUBTITLE_FRACTION).height(LINE))
        }
    }
}

/**
 * A short run of placeholder rows under one shimmer.
 *
 * Deliberately a fixed few rather than filling the screen: the point is to show the shape of
 * what is coming, and a full screen of grey blocks is its own kind of noise.
 */
@Composable
fun MediaListSkeleton(modifier: Modifier = Modifier, rows: Int = SKELETON_ROWS) {
    Column(modifier.shimmer()) {
        repeat(rows) { MediaRowSkeleton() }
    }
}

@Composable
private fun Block(modifier: Modifier) {
    Spacer(
        modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp),
            ),
    )
}

private const val SWEEP_MS = 1_100
private const val SWEEP_TRAVEL = 0.45f
private val THUMB_WIDTH = 100.dp
private val THUMB_HEIGHT = 56.dp
private val LINE = 12.dp
private const val TITLE_FRACTION = 0.9f
private const val SUBTITLE_FRACTION = 0.5f
private const val SKELETON_ROWS = 6
