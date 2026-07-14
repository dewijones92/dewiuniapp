package com.dewijones92.uniapp.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.video.VideoQuality

/** The current video's quality options and how to switch. */
data class QualityControl(
    val options: List<VideoQuality>,
    val selectedId: String?,
    val onSelect: (String) -> Unit,
) {
    companion object {
        val None: QualityControl = QualityControl(emptyList(), null, {})
    }
}

/**
 * The video quality row on the full player: the available resolutions, the
 * current one highlighted. Shown only when there's more than one to choose,
 * and scrollable since a video can offer many (360p … 4K).
 */
@Composable
internal fun QualitySelector(quality: QualityControl, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Outlined.HighQuality,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        quality.options.forEach { option ->
            TextButton(onClick = { quality.onSelect(option.id) }) {
                Text(
                    text = option.label,
                    color = if (option.id == quality.selectedId) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
