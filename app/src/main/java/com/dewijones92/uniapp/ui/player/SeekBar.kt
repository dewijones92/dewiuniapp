package com.dewijones92.uniapp.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.domain.SkipSegment
import com.dewijones92.uniapp.playback.PlaybackState

/**
 * The scrubber: a slider over the current position, the elapsed/total times,
 * and (for anything with skip segments) a SponsorBlock-style strip under it.
 * Used both below the artwork (audio) and overlaid on the video.
 */
@Composable
internal fun SeekBar(state: PlaybackState, onSeekTo: (Long) -> Unit, modifier: Modifier = Modifier) {
    val duration = state.durationMs
    var dragValue by remember(state.positionMs) { mutableStateOf<Float?>(null) }
    val position = dragValue?.toLong() ?: state.positionMs

    Column(modifier = modifier.fillMaxWidth()) {
        if (duration != null) {
            Slider(
                value = position.coerceIn(0, duration).toFloat(),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { onSeekTo(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..duration.toFloat(),
            )
            SkipSegmentBar(state.skipSegments, duration)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Text(
                text = formatTime(state.positionMs),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * A thin SponsorBlock-style strip under the scrubber marking the skip segments
 * (green), so you can see what will be auto-skipped. Inset to line up with the
 * slider's track (which is padded by the thumb radius). Nothing drawn when
 * there are no segments.
 */
@Composable
private fun SkipSegmentBar(segments: List<SkipSegment>, durationMs: Long) {
    if (segments.isEmpty() || durationMs <= 0) return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SLIDER_THUMB_INSET)
            .height(SEGMENT_BAR_HEIGHT),
    ) {
        segments.forEach { segment ->
            val startX = (segment.start.inWholeMilliseconds.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
            val endX = (segment.end.inWholeMilliseconds.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
            drawRect(
                color = SponsorSegmentColor,
                topLeft = Offset(startX, 0f),
                size = Size((endX - startX).coerceAtLeast(1f), size.height),
            )
        }
    }
}

/**
 * The sponsor segments spelled out as time ranges (e.g. "2:10 – 3:05"), in the
 * same green as the strip, so it's clear exactly what gets auto-skipped and when.
 */
@Composable
internal fun SponsorSegments(segments: List<SkipSegment>, modifier: Modifier = Modifier) {
    if (segments.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sponsor_segments_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        segments.sortedBy { it.start }.forEach { segment ->
            val range = "${formatTime(segment.start.inWholeMilliseconds)} – " +
                formatTime(segment.end.inWholeMilliseconds)
            Text(
                text = range,
                style = MaterialTheme.typography.labelLarge,
                color = SponsorSegmentColor,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

private val SLIDER_THUMB_INSET = 10.dp
private val SEGMENT_BAR_HEIGHT = 4.dp
private val SponsorSegmentColor = Color(0xFF2ECC71) // SponsorBlock's brand green
