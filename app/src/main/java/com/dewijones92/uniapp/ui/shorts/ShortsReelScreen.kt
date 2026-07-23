package com.dewijones92.uniapp.ui.shorts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.playback.PlaybackState

/**
 * A full-screen vertical Shorts reel: swipe up/down between shorts, each playing
 * through the one shared player (resolved just-in-time when it settles), tap to
 * play/pause. A finished short rolls on to the next. Uses the same playback
 * session as everything else, so closing the reel keeps it playing in the mini
 * player.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ShortsReelScreen(
    container: AppContainer,
    shorts: List<MediaItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    if (shorts.isEmpty()) {
        ShortsEmpty(onBack, modifier)
        return
    }
    val playback = container.playbackController
    val launcher = container.videoPlaybackLauncher
    val state by playback.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { shorts.size })

    // Resolve + play whichever short the pager rests on (URLs expire, so play at rest).
    LaunchedEffect(pager.settledPage) {
        shorts.getOrNull(pager.settledPage)?.mediaUrl?.let { launcher.play(it, shorts[pager.settledPage].sourceId) }
    }
    // When a short finishes, roll on to the next — once per genuine end. Seed the
    // already-handled id so a retained `hasEnded` from an item that ended before the
    // reel opened doesn't immediately skip past the first short.
    var handledEndFor by remember { mutableStateOf(state?.takeIf { it.hasEnded }?.itemId) }
    LaunchedEffect(state?.itemId, state?.hasEnded) {
        val current = state ?: return@LaunchedEffect
        if (!current.hasEnded || handledEndFor == current.itemId) return@LaunchedEffect
        handledEndFor = current.itemId
        if (pager.currentPage < shorts.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
    }

    Surface(color = Color.Black, modifier = modifier.fillMaxSize()) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ShortPage(
                short = shorts[page],
                player = playback.player,
                isCurrent = page == pager.currentPage,
                state = state,
                onTogglePlayPause = playback::togglePlayPause,
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
        }
    }
}

/** One reel page: the short's video (only bound on the current page), a buffering spinner, and its title. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ShortPage(
    short: MediaItem,
    player: Player?,
    isCurrent: Boolean,
    state: PlaybackState?,
    onTogglePlayPause: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlayPause,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isCurrent && player != null && state?.hasVideo == true) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isCurrent && state?.isBuffering == true) {
            CircularProgressIndicator(color = Color.White)
        }
        Text(
            text = short.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Composable
private fun ShortsEmpty(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    Surface(color = Color.Black, modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.shorts_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
        }
    }
}
