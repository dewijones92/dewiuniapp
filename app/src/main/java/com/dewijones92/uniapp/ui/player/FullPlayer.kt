package com.dewijones92.uniapp.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.innertube.comments.Comment
import com.dewijones92.uniapp.playback.PlaybackState
import com.dewijones92.uniapp.ui.common.MediaThumbnail
import com.dewijones92.uniapp.ui.player.WatchViewModel.CommentsState
import com.dewijones92.uniapp.ui.player.WatchViewModel.PostState
import java.util.concurrent.TimeUnit

/**
 * Full "now playing" screen, opened from the mini player. Drives the one
 * [com.dewijones92.uniapp.playback.PlaybackController], so it serves podcast
 * episodes and videos identically.
 */
@Composable
fun FullPlayerDialog(
    state: PlaybackState,
    player: Player?,
    comments: CommentsState,
    watchActions: WatchActions,
    quality: QualityControl,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSetSpeed: (Float) -> Unit,
) {
    KeepScreenOnWhilePlayingVideo(active = state.hasVideo && state.isPlaying)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }

                PlayerStage(state, player)

                Spacer(Modifier.height(if (state.hasVideo) 24.dp else 48.dp))
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                state.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(48.dp))
                SeekBar(state, onSeekTo)

                Spacer(Modifier.height(24.dp))
                TransportControls(state, onTogglePlayPause, onSeekBackward, onSeekForward)

                Spacer(Modifier.height(24.dp))
                SpeedControl(state.speed, onSetSpeed)

                // Quality — video only, and only when there's a choice to make.
                if (state.hasVideo && quality.options.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    QualitySelector(quality)
                }

                // Like — signed-in write action, keyed by the current video.
                if (state.hasVideo && watchActions.canAct) {
                    Spacer(Modifier.height(16.dp))
                    LikeButton(watchActions.liked, watchActions.onToggleLike)
                }

                // Description / show notes — unified: a video's description and
                // a podcast episode's notes are the same field, shown the same way.
                val description = state.description
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(24.dp))
                    DescriptionSection(description)
                }

                // Comments live under the video, YouTube-style; audio items have none.
                if (state.hasVideo) {
                    Spacer(Modifier.height(32.dp))
                    CommentsSection(comments, watchActions)
                }
            }
        }
    }
}

/** The signed-in write actions available on the watch page. */
data class WatchActions(
    val canAct: Boolean,
    val liked: Boolean,
    val onToggleLike: () -> Unit,
    val postState: PostState,
    val onPostComment: (String) -> Unit,
    val onPostHandled: () -> Unit,
) {
    companion object {
        /** No account connected: reading only. */
        val ReadOnly: WatchActions = WatchActions(false, false, {}, PostState.Idle, {}, {})
    }
}

@Composable
private fun LikeButton(liked: Boolean, onToggleLike: () -> Unit) {
    TextButton(onClick = onToggleLike) {
        Icon(
            imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = stringResource(R.string.like),
            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (liked) R.string.liked else R.string.like),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun CommentsSection(comments: CommentsState, watchActions: WatchActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.comments_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (watchActions.canAct) {
            CommentComposer(watchActions)
        }
        when (comments) {
            CommentsState.Loading -> CommentsNote(stringResource(R.string.comments_loading))
            CommentsState.Disabled -> CommentsNote(stringResource(R.string.comments_disabled))
            CommentsState.Error -> CommentsNote(stringResource(R.string.comments_error))
            is CommentsState.Loaded ->
                if (comments.comments.isEmpty()) {
                    CommentsNote(stringResource(R.string.comments_empty))
                } else {
                    comments.comments.forEach { comment -> CommentRow(comment) }
                }
        }
    }
}

@Composable
private fun CommentComposer(watchActions: WatchActions) {
    var text by remember { mutableStateOf("") }
    // Clear the box once a post lands, then reset the state (as an effect, not in composition).
    LaunchedEffect(watchActions.postState) {
        if (watchActions.postState == PostState.Posted) {
            text = ""
            watchActions.onPostHandled()
        }
    }
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.comment_hint)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = watchActions.postState != PostState.Posting,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (watchActions.postState == PostState.Failed) {
                Text(
                    text = stringResource(R.string.comment_failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            TextButton(
                onClick = { watchActions.onPostComment(text) },
                enabled = text.isNotBlank() && watchActions.postState != PostState.Posting,
            ) { Text(stringResource(R.string.comment_post)) }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        val author = buildString {
            append(comment.author)
            comment.publishedTime?.let { append("  ·  ").append(it) }
        }
        Text(
            text = author,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = comment.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        val repliesLabel = if (comment.replyCount > 0) {
            stringResource(R.string.comments_replies, comment.replyCount)
        } else {
            null
        }
        val meta = buildString {
            comment.likeCount?.let { append("👍 ").append(it) }
            repliesLabel?.let {
                if (isNotEmpty()) append("   ")
                append(it)
            }
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CommentsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Collapsible description / show-notes block. Starts clamped to a few lines
 * with a "Show more" toggle, so a long description doesn't push the comments
 * off-screen. Tapping the whole block toggles too.
 */
@Composable
private fun DescriptionSection(description: String) {
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Text(
            text = stringResource(R.string.description_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(if (expanded) R.string.show_less else R.string.show_more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The one stage at the top of the player. A video track shows the shared
 * player's surface (as soon as the track exists — before the first frame — so
 * decoding can start, defaulting to 16:9 until the real aspect ratio arrives);
 * an audio item (a podcast) shows its artwork through the same [MediaThumbnail]
 * every list uses. Two pillars, one stage.
 */
@Composable
private fun PlayerStage(state: PlaybackState, player: Player?) {
    if (player != null && state.hasVideo) {
        VideoStage(player, state.videoAspectRatio ?: DEFAULT_VIDEO_ASPECT_RATIO)
    } else {
        MediaThumbnail(
            url = HttpUrl.parse(state.artworkUrl.orEmpty()),
            contentDescription = state.title,
            modifier = Modifier
                .widthIn(max = ARTWORK_MAX_WIDTH)
                .fillMaxWidth()
                .aspectRatio(1f),
        )
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoStage(player: Player, aspectRatio: Float, modifier: Modifier = Modifier) {
    // A TextureView composes cleanly inside the dialog (no SurfaceView z-order
    // punch-through); PlayerSurface attaches/detaches it to the player for us.
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
    )
}

/** Stops the screen dimming while a video is actually playing. */
@Composable
private fun KeepScreenOnWhilePlayingVideo(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun SeekBar(state: PlaybackState, onSeekTo: (Long) -> Unit, modifier: Modifier = Modifier) {
    val duration = state.durationMs
    var dragValue by remember(state.positionMs) { mutableStateOf<Float?>(null) }
    val position = dragValue?.toLong() ?: state.positionMs

    Column(modifier = modifier.fillMaxWidth()) {
        if (duration != null) {
            Slider(
                value = position.coerceIn(0, duration).toFloat(),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { onSeekTo(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..duration.toFloat(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Text(
                text = formatTime(state.positionMs),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSeekBackward, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.Replay10,
                contentDescription = stringResource(R.string.seek_back),
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .size(72.dp),
        ) {
            val icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
            val desc = if (state.isPlaying) R.string.pause else R.string.play
            Icon(icon, contentDescription = stringResource(desc), modifier = Modifier.size(40.dp))
        }
        IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.Forward30,
                contentDescription = stringResource(R.string.seek_forward),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun SpeedControl(speed: Float, onSetSpeed: (Float) -> Unit, modifier: Modifier = Modifier) {
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
        SPEEDS.forEach { option ->
            TextButton(onClick = { onSetSpeed(option) }) {
                Text(
                    text = "${option}x",
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

private fun formatTime(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
}

private val SPEEDS = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
private const val SECONDS_PER_MINUTE = 60L
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private val ARTWORK_MAX_WIDTH = 320.dp
private const val DESCRIPTION_COLLAPSED_LINES = 4
