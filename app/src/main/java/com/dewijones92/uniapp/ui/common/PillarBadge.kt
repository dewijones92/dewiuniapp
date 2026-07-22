package com.dewijones92.uniapp.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.domain.MediaKind

/** The icon that marks each pillar — shared so every surface uses the same glyph. */
fun pillarIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.VIDEO -> Icons.Filled.OndemandVideo
    MediaKind.PODCAST -> Icons.Filled.Podcasts
}

private fun pillarLabelRes(kind: MediaKind): Int = when (kind) {
    MediaKind.VIDEO -> R.string.pillar_video
    MediaKind.PODCAST -> R.string.pillar_podcast
}

/**
 * Little icon + label saying which pillar the current item is — a YouTube video
 * or a podcast. One badge, used by the full player and (icon-only) the mini
 * player, so the distinction reads the same everywhere.
 */
@Composable
fun PillarBadge(kind: MediaKind, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = pillarIcon(kind),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(pillarLabelRes(kind)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
