package com.dewijones92.totum.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup

/**
 * Draws the player's current subtitle cues over the video.
 *
 * Read straight from the player rather than parsed by us: Media3 already decodes the text
 * track it selected, so anything we did here would be a second, disagreeing implementation
 * of the same thing. The player is the single source of truth for what should be on screen
 * right now.
 *
 * White on a translucent black slab, which is the one styling that stays legible over an
 * arbitrary frame — the reason every player converges on it.
 */
@Composable
internal fun SubtitleCues(player: Player, modifier: Modifier = Modifier) {
    var lines by remember(player) { mutableStateOf(emptyList<String>()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                lines = cueGroup.cues.mapNotNull { it.text?.toString()?.trim()?.ifBlank { null } }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (lines.isEmpty()) return
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        lines.forEach { line ->
            Text(
                text = line,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = CUE_BACKGROUND_ALPHA))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private const val CUE_BACKGROUND_ALPHA = 0.6f
