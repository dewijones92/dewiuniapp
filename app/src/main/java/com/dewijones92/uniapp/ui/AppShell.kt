package com.dewijones92.uniapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.di.fake.FakeAppContainer
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.navigation.TopLevelDestination
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.playback.PlaybackState
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.account.AccountScreen
import com.dewijones92.uniapp.ui.common.MiniPlayerBar
import com.dewijones92.uniapp.ui.common.RequestNotificationPermissionOnFirstPlay
import com.dewijones92.uniapp.ui.library.LibraryScreen
import com.dewijones92.uniapp.ui.player.CommentReplies
import com.dewijones92.uniapp.ui.player.FullPlayerOverlay
import com.dewijones92.uniapp.ui.player.QualityControl
import com.dewijones92.uniapp.ui.player.QueueControls
import com.dewijones92.uniapp.ui.player.WatchActions
import com.dewijones92.uniapp.ui.player.WatchViewModel
import com.dewijones92.uniapp.ui.podcasts.PodcastsScreen
import com.dewijones92.uniapp.ui.search.SearchScreen
import com.dewijones92.uniapp.ui.shorts.ShortsReelScreen
import com.dewijones92.uniapp.ui.videos.VideosScreen

/**
 * Top-level scaffold: bottom navigation across the app's pillars with
 * animated transitions between them.
 */
@Composable
fun AppShell(container: AppContainer, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(TopLevelDestination.Videos) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var shortsReel by remember { mutableStateOf<List<MediaItem>?>(null) }
    val playbackState by container.playbackController.state.collectAsStateWithLifecycle()
    val controller = container.playbackController
    val watchViewModel: WatchViewModel = viewModel(factory = WatchViewModel.factory(container))

    RequestNotificationPermissionOnFirstPlay(playbackActive = playbackState != null)
    // End-of-item advance lives here, always composed — so the queue advances in the
    // mini player / with the screen off, not only while the full player is expanded.
    AutoAdvance(playbackState, watchViewModel, container, reelOpen = shortsReel != null)

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    playbackState?.let { state ->
                        MiniPlayerBar(
                            state = state,
                            onTogglePlayPause = controller::togglePlayPause,
                            onExpand = { showFullPlayer = true },
                        )
                    }
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            val isSelected = destination == selected
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selected = destination },
                                icon = {
                                    val icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon
                                    Icon(imageVector = icon, contentDescription = null)
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = selected,
                modifier = Modifier.padding(innerPadding),
                label = "top-level-destination",
            ) { destination ->
                when (destination) {
                    TopLevelDestination.Videos -> VideosScreen(container, onOpenShorts = { shortsReel = it })
                    TopLevelDestination.Podcasts -> PodcastsScreen(container)
                    TopLevelDestination.Search -> SearchScreen(container)
                    TopLevelDestination.Library -> LibraryScreen(container)
                    TopLevelDestination.Account -> AccountScreen(container)
                }
            }
        }

        // Full player overlays the whole app (above the mini player + nav) when
        // expanded; the mini player keeps the audio/video running underneath.
        playbackState?.takeIf { showFullPlayer }?.let { state ->
            FullPlayerHost(state, controller, container, watchViewModel) { showFullPlayer = false }
        }

        // The Shorts reel is a full-screen overlay (above the nav + mini player),
        // so vertical swipes page between shorts without the app chrome in the way.
        shortsReel?.let { shorts ->
            ShortsReelScreen(container, shorts, onBack = { shortsReel = null })
        }
    }
}

/**
 * Binds the watch view model to the current video and advances at end-of-item.
 * Always composed (independent of the full player) so the queue keeps advancing
 * in the mini player and with the screen off. Fires once per genuine end — a
 * retained `hasEnded` from a previous item (e.g. on first composition) is seeded
 * as already-handled so it can't trigger a spurious skip — and stands down while
 * the Shorts reel, which drives its own advancement, is up.
 */
@Composable
private fun AutoAdvance(
    state: PlaybackState?,
    watchViewModel: WatchViewModel,
    container: AppContainer,
    reelOpen: Boolean,
) {
    LaunchedEffect(state?.itemId, state?.hasVideo) {
        if (state != null && state.hasVideo) watchViewModel.bind(state.itemId.value)
    }
    var handledEndFor by remember { mutableStateOf(state?.takeIf { it.hasEnded }?.itemId) }
    LaunchedEffect(state?.itemId, state?.hasEnded) {
        if (reelOpen) return@LaunchedEffect
        val ended = state?.takeIf { it.hasEnded } ?: return@LaunchedEffect
        if (handledEndFor == ended.itemId) return@LaunchedEffect
        handledEndFor = ended.itemId
        if (!container.playbackQueue.playNextInQueue() && ended.hasVideo) {
            watchViewModel.autoplayNext()
        }
    }
}

/** Hosts the full-player overlay, wiring it to the one playback controller. */
@Composable
private fun FullPlayerHost(
    state: PlaybackState,
    controller: PlaybackController,
    container: AppContainer,
    watchViewModel: WatchViewModel,
    onDismiss: () -> Unit,
) {
    val comments by watchViewModel.comments.collectAsStateWithLifecycle()
    val replies by watchViewModel.replies.collectAsStateWithLifecycle()
    val related by watchViewModel.related.collectAsStateWithLifecycle()
    val sleepTimer by container.sleepTimer.state.collectAsStateWithLifecycle()
    val signedIn by watchViewModel.signedIn.collectAsStateWithLifecycle()
    val rating by watchViewModel.rating.collectAsStateWithLifecycle()
    val inWatchLater by watchViewModel.inWatchLater.collectAsStateWithLifecycle()
    val postState by watchViewModel.postState.collectAsStateWithLifecycle()
    val quality by watchViewModel.quality.collectAsStateWithLifecycle()
    val upNext by container.playbackQueue.upNext.collectAsStateWithLifecycle()

    FullPlayerOverlay(
        state = state,
        player = controller.player,
        comments = comments,
        replies = CommentReplies(
            threads = replies,
            onToggle = watchViewModel::toggleReplies,
            onLoadMore = watchViewModel::loadMoreReplies,
        ),
        related = related,
        watchActions = WatchActions(
            canAct = signedIn,
            rating = rating,
            inWatchLater = inWatchLater,
            onToggleLike = watchViewModel::toggleLike,
            onToggleDislike = watchViewModel::toggleDislike,
            onToggleWatchLater = watchViewModel::toggleWatchLater,
            postState = postState,
            onPostComment = watchViewModel::postComment,
            onPostHandled = watchViewModel::clearPostState,
        ),
        quality = QualityControl(
            options = quality.options,
            selectedId = quality.selectedId,
            onSelect = watchViewModel::selectQuality,
            canListen = quality.canListen,
            onListen = watchViewModel::listen,
        ),
        sleepTimer = sleepTimer,
        onDismiss = onDismiss,
        onPlayRelated = watchViewModel::playRelated,
        onStartSleep = container.sleepTimer::start,
        onCancelSleep = container.sleepTimer::cancel,
        onTogglePlayPause = controller::togglePlayPause,
        onSeekTo = controller::seekTo,
        onSeekBackward = controller::seekBackward,
        onSeekForward = controller::seekForward,
        onSetSpeed = controller::setSpeed,
        onSetSkipSilence = controller::setSkipSilence,
        queue = QueueControls(
            upNext = upNext,
            onPlay = container.playbackQueue::playFromQueue,
            onRemove = container.playbackQueue::removeAt,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun AppShellPreview() {
    UniAppTheme { AppShell(FakeAppContainer()) }
}
