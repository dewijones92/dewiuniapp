package com.dewijones92.uniapp.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R

/** Toggles trimming of near-silent stretches (dead air), podcast-app style. */
@Composable
internal fun SkipSilenceControl(
    enabled: Boolean,
    onSetSkipSilence: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Icon(
            Icons.Outlined.GraphicEq,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.skip_silence), modifier = Modifier.padding(start = 8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onSetSkipSilence,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
