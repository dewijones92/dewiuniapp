package com.dewijones92.uniapp.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.playback.PlaybackState
import java.util.concurrent.TimeUnit

/**
 * Full "now playing" screen, opened from the mini player. Drives the one
 * [com.dewijones92.uniapp.playback.PlaybackController], so it serves podcast
 * episodes and videos identically.
 */
@Composable
fun FullPlayerDialog(
    state: PlaybackState,
    player: Player?,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSetSpeed: (Float) -> Unit,
) {
    KeepScreenOnWhilePlayingVideo(active = state.hasVideo && state.isPlaying)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }

                // The one place video is shown: the shared player's stage. Shown
                // as soon as a video track exists (before the first frame, so
                // decoding can start), defaulting to 16:9 until the real aspect
                // ratio is reported. Audio items (podcasts) skip it entirely.
                if (player != null && state.hasVideo) {
                    VideoStage(player, state.videoAspectRatio ?: DEFAULT_VIDEO_ASPECT_RATIO)
                }

                Spacer(Modifier.height(if (state.hasVideo) 24.dp else 48.dp))
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                state.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(48.dp))
                SeekBar(state, onSeekTo)

                Spacer(Modifier.height(24.dp))
                TransportControls(state, onTogglePlayPause, onSeekBackward, onSeekForward)

                Spacer(Modifier.height(24.dp))
                SpeedControl(state.speed, onSetSpeed)
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoStage(player: Player, aspectRatio: Float, modifier: Modifier = Modifier) {
    // A TextureView composes cleanly inside the dialog (no SurfaceView z-order
    // punch-through); PlayerSurface attaches/detaches it to the player for us.
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
    )
}

/** Stops the screen dimming while a video is actually playing. */
@Composable
private fun KeepScreenOnWhilePlayingVideo(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun SeekBar(state: PlaybackState, onSeekTo: (Long) -> Unit, modifier: Modifier = Modifier) {
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

@Composable
private fun TransportControls(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSeekBackward, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.Replay10,
                contentDescription = stringResource(R.string.seek_back),
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .size(72.dp),
        ) {
            val icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
            val desc = if (state.isPlaying) R.string.pause else R.string.play
            Icon(icon, contentDescription = stringResource(desc), modifier = Modifier.size(40.dp))
        }
        IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.Forward30,
                contentDescription = stringResource(R.string.seek_forward),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun SpeedControl(speed: Float, onSetSpeed: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Outlined.Speed,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SPEEDS.forEach { option ->
            TextButton(onClick = { onSetSpeed(option) }) {
                Text(
                    text = "${option}x",
                    color = if (option == speed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
}

private val SPEEDS = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
private const val SECONDS_PER_MINUTE = 60L
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
