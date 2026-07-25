package com.dewijones92.totum.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.totum.R
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.PlayState

/**
 * The one status line every row shows: which pillar the item is, whether it's held
 * offline, and whether it's been played. One component, so the three signals read
 * consistently in every list — feeds, search, queue, history, playlists, Library.
 *
 * Deliberately quiet for the default case: an unplayed, streaming item shows only its
 * pillar glyph. State that shouts on every row stops carrying information.
 */
@Composable
internal fun MediaItemStatus(
    pillar: MediaKind,
    playState: PlayState,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        StatusIcon(
            icon = if (pillar == MediaKind.PODCAST) Icons.Filled.Podcasts else Icons.Filled.SmartDisplay,
            descriptionRes = if (pillar == MediaKind.PODCAST) R.string.pillar_podcast else R.string.pillar_video,
        )
        // Distinct from the trailing download button, which is the *action*: this says
        // "you have this offline" without also meaning "tap to delete it". A down-arrow
        // rather than another check, so it can't be read as the played tick.
        if (downloadState is DownloadState.Downloaded) {
            StatusIcon(Icons.Filled.DownloadForOffline, R.string.status_offline)
        }
        if (playState.isPlayed) {
            StatusIcon(Icons.Filled.Check, R.string.status_played)
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, descriptionRes: Int) {
    Icon(
        imageVector = icon,
        contentDescription = stringResource(descriptionRes),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(STATUS_ICON_SIZE),
    )
}

/**
 * A thin sliver under the thumbnail showing how far in an item is — far more
 * informative at a glance than a "part-way" label, and the reason progress isn't in
 * the icon row. Nothing is drawn for unplayed or played items, or while the duration
 * is unknown, so the sliver only ever means "you are here".
 */
@Composable
internal fun PlayProgressSliver(playState: PlayState, modifier: Modifier = Modifier) {
    val fraction = (playState as? PlayState.InProgress)?.fraction ?: return
    Spacer(Modifier.height(2.dp))
    LinearProgressIndicator(
        progress = { fraction },
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
        modifier = modifier.height(SLIVER_HEIGHT),
    )
}

/** Dims a played item's title, so a finished row recedes without disappearing. */
@Composable
internal fun playedTitleAlpha(playState: PlayState): Float =
    if (playState.isPlayed) PLAYED_TITLE_ALPHA else 1f

private val STATUS_ICON_SIZE = 14.dp
private val SLIVER_HEIGHT = 3.dp
private const val PLAYED_TITLE_ALPHA = 0.55f

/** Padding that keeps the status row visually attached to the text above it. */
internal val StatusRowSpacing = Modifier.padding(top = 3.dp)
