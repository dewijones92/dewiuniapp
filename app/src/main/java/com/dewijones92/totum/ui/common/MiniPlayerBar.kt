package com.dewijones92.totum.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.totum.R
import com.dewijones92.totum.playback.PlaybackState

/**
 * Persistent now-playing bar shown above the bottom navigation whenever
 * something is queued — identical for podcast episodes and videos.
 */
@Composable
fun MiniPlayerBar(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buffering = stringResource(R.string.buffering)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Marks the pillar at a glance — video vs podcast.
                Icon(
                    imageVector = pillarIcon(state.kind),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                // The bar is where you spend most of the time, and it was the one
                // surface that showed nothing while stalled — so a stall here was
                // indistinguishable from the app simply having stopped.
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(20.dp)
                            .semantics { contentDescription = buffering },
                    )
                } else {
                    IconButton(onClick = onTogglePlayPause) {
                        if (state.isPlaying) {
                            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.pause))
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play))
                        }
                    }
                }
            }
            state.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
