package com.dewijones92.uniapp.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.ui.common.MediaThumbnail
import com.dewijones92.uniapp.ui.player.WatchViewModel.RelatedState

/**
 * The "up next" list on the watch page: the video's related videos, tappable to
 * play. Sits below the description, above comments. When the current video ends
 * the top entry autoplays (driven from the shell), so this is both a picker and
 * the visible queue.
 */
@Composable
internal fun RelatedSection(
    related: RelatedState,
    onPlayRelated: (FeedVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.related_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (related) {
            RelatedState.Loading -> PlayerNote(stringResource(R.string.related_loading))
            RelatedState.Error -> PlayerNote(stringResource(R.string.related_error))
            is RelatedState.Loaded ->
                if (related.videos.isEmpty()) {
                    PlayerNote(stringResource(R.string.related_empty))
                } else {
                    related.videos.forEach { video ->
                        RelatedVideoRow(video, onPlay = { onPlayRelated(video) })
                    }
                }
        }
    }
}

/**
 * One related video: a 16:9 still, title and channel — the same thumbnail seam
 * ([MediaThumbnail]) every list uses. Tapping plays it through the one launcher.
 */
@Composable
private fun RelatedVideoRow(video: FeedVideo, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 8.dp),
    ) {
        MediaThumbnail(
            url = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.size(width = RELATED_THUMBNAIL_WIDTH, height = RELATED_THUMBNAIL_HEIGHT),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                video.author?.let { append(it) }
                video.durationSeconds?.let {
                    if (isNotEmpty()) append("  ·  ")
                    append(formatTime(it * MILLIS_PER_SECOND))
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private const val MILLIS_PER_SECOND = 1000L
private val RELATED_THUMBNAIL_WIDTH = 120.dp
private val RELATED_THUMBNAIL_HEIGHT = 68.dp
