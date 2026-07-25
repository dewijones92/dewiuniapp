package com.dewijones92.totum.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * One labelled on/off playback preference in the full player (skip silences,
 * auto-play next, …). One component so the row of them stays visually consistent
 * as more are added.
 */
@Composable
fun PlayerToggle(
    icon: ImageVector,
    labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The full player's on/off playback preferences, bundled so they thread as one. */
data class PlaybackToggles(
    val skipSilence: Boolean = false,
    val onSetSkipSilence: (Boolean) -> Unit = {},
    val autoPlayNext: Boolean = true,
    val onSetAutoPlayNext: (Boolean) -> Unit = {},
    val onSetVolumeBoost: (com.dewijones92.totum.playback.VolumeBoost) -> Unit = {},
)
