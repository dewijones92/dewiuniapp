package com.dewijones92.totum.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
            // Described, like the boost icon beside it: the buttons say "1×, 1.5×, 2×" and without
            // this a screen reader gives no clue what they set.
            contentDescription = stringResource(R.string.playback_speed),
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
            // Described rather than null: the four buttons beside it say "Off / Low / Med / High",
            // so without this a screen reader announces four levels of nothing. Found while writing
            // the control inventory, which could not name this control either.
            contentDescription = stringResource(R.string.volume_boost),
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

/**
 * Icon, current value, tap for the options — the shape the sleep timer already had.
 *
 * Speed and volume boost each used to be a full-width row of every option laid out at once, which is
 * two whole bands of the screen spent on settings that are changed rarely and read often. As a
 * picker they show the CURRENT value in one compact button and put the rest one tap away, which is
 * what lets five stacked control rows become a single strip.
 *
 * Shared rather than written twice, and shaped like `SleepTimerControl` on purpose: three controls
 * side by side that behave differently would be worse than the rows they replaced.
 */
@Composable
internal fun <T> CompactPicker(
    icon: ImageVector,
    label: String,
    current: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Text(text = optionLabel(current), modifier = Modifier.padding(start = 6.dp))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(optionLabel(option)) },
                leadingIcon = {
                    if (option == current) Icon(Icons.Filled.Check, contentDescription = null)
                },
                onClick = {
                    onSelect(option)
                    open = false
                },
            )
        }
    }
}

/** Speed as a compact picker — see [CompactPicker]. */
@Composable
internal fun SpeedPicker(speed: Float, onSetSpeed: (Float) -> Unit, modifier: Modifier = Modifier) {
    CompactPicker(
        icon = Icons.Outlined.Speed,
        label = stringResource(R.string.playback_speed),
        current = speed,
        options = PlaybackSpeeds,
        optionLabel = { speedLabel(it) },
        onSelect = onSetSpeed,
        modifier = modifier,
    )
}

/** Volume boost as a compact picker — see [CompactPicker]. */
@Composable
internal fun BoostPicker(boost: VolumeBoost, onSetBoost: (VolumeBoost) -> Unit, modifier: Modifier = Modifier) {
    CompactPicker(
        icon = Icons.AutoMirrored.Outlined.VolumeUp,
        label = stringResource(R.string.volume_boost),
        current = boost,
        options = VolumeBoost.entries,
        optionLabel = { stringResource(it.labelRes()) },
        onSelect = onSetBoost,
        modifier = modifier,
    )
}
