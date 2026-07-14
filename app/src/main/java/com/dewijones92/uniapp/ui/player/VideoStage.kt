package com.dewijones92.uniapp.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.playback.PlaybackState
import kotlinx.coroutines.delay

/**
 * The video with its controls overlaid, modern-player style: the transport
 * (skip / play-pause) sits centred over the picture and the seek bar along the
 * bottom, on a subtle scrim. Tapping the video toggles the controls, and they
 * auto-hide after a few seconds while playing. Everything else (title,
 * description, comments) scrolls below, in FullPlayer.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun VideoStageWithControls(
    state: PlaybackState,
    player: Player,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    // Auto-hide while playing; any toggle restarts the timer via the key change.
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(CONTROLS_AUTOHIDE_MS)
            controlsVisible = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(state.videoAspectRatio ?: DEFAULT_VIDEO_ASPECT_RATIO)
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier.matchParentSize(),
        )
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            VideoControlsOverlay(state, onDismiss, onTogglePlayPause, onSeekTo, onSeekBackward, onSeekForward)
        }
    }
}

/** The scrim + white controls drawn over the video when they're visible. */
@Composable
private fun VideoControlsOverlay(
    state: PlaybackState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
) {
    // White content over a dark scrim so controls read against any frame.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
            TransportControls(
                state,
                onTogglePlayPause,
                onSeekBackward,
                onSeekForward,
                modifier = Modifier.align(Alignment.Center),
            )
            SeekBar(
                state = state,
                onSeekTo = onSeekTo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private const val CONTROLS_AUTOHIDE_MS = 3_000L
private const val SCRIM_ALPHA = 0.35f
