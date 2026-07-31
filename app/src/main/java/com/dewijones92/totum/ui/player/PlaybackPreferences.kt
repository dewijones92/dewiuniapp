package com.dewijones92.totum.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.totum.R
import com.dewijones92.totum.playback.VolumeBoost

/**
 * The offered playback rates, in one place — the below-artwork audio control and the
 * on-video overlay menu both read this, so they can't drift apart.
 *
 * Reaches 3x because a podcast at 3x is a real use (a slow talker), where a video rarely
 * is; one list serving both is simpler than two that mostly overlap.
 */
internal val PlaybackSpeeds: List<Float> = listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

/** "1x", "1.5x" — trims the pointless ".0" a raw Float would show. */
internal fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/**
 * The full player's playback preferences: rate, silence handling, auto-advance and
 * volume boost. Split out of the player itself because it had grown into a stack of
 * controls — and because these are the ones destined to move behind a single settings
 * affordance on the video (see docs/todos/ui-polish.md).
 */

/** The player's on/off preferences. */
@Composable
internal fun PlaybackTogglesRow(skipSilence: Boolean, toggles: PlaybackToggles) {
    // Both pillars now: silence is handled by raising the playback rate, which retimes
    // audio and video together, so the old audio-only restriction is gone.
    PlayerToggle(
        icon = Icons.Outlined.GraphicEq,
        labelRes = R.string.skip_silence,
        checked = skipSilence,
        onCheckedChange = toggles.onSetSkipSilence,
    )
    PlayerToggle(
        icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
        labelRes = R.string.auto_play_next,
        checked = toggles.autoPlayNext,
        onCheckedChange = toggles.onSetAutoPlayNext,
    )
    // Experimental, and labelled as such: a ~150ms start against 2-4s, but SABR is asked for a
    // media time rather than a byte offset so it cannot seek yet.
    PlayerToggle(
        icon = Icons.Outlined.Bolt,
        labelRes = R.string.sabr_playback,
        checked = toggles.sabrPlayback,
        onCheckedChange = toggles.onSetSabrPlayback,
    )
}

@Composable
internal fun SpeedControl(speed: Float, onSetSpeed: (Float) -> Unit, modifier: Modifier = Modifier) {
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
        PlaybackSpeeds.forEach { option ->
            TextButton(onClick = { onSetSpeed(option) }) {
                Text(
                    text = speedLabel(option),
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

/**
 * Lifts quiet audio. Levels rather than a slider: the useful range is small, and the
 * choice is remembered per source, so a quietly recorded podcast stays boosted without
 * shouting everywhere else.
 */
@Composable
internal fun BoostControl(boost: VolumeBoost, onSetBoost: (VolumeBoost) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VolumeBoost.entries.forEach { option ->
            TextButton(onClick = { onSetBoost(option) }) {
                Text(
                    text = stringResource(option.labelRes()),
                    color = if (option == boost) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun VolumeBoost.labelRes(): Int = when (this) {
    VolumeBoost.OFF -> R.string.boost_off
    VolumeBoost.LOW -> R.string.boost_low
    VolumeBoost.MEDIUM -> R.string.boost_medium
    VolumeBoost.HIGH -> R.string.boost_high
}
