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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.di.fake.FakeAppContainer
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
import com.dewijones92.uniapp.ui.videos.VideosScreen

/**
 * Top-level scaffold: bottom navigation across the app's pillars with
 * animated transitions between them.
 */
@Composable
fun AppShell(container: AppContainer, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(TopLevelDestination.Videos) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    val playbackState by container.playbackController.state.collectAsStateWithLifecycle()
    val controller = container.playbackController

    RequestNotificationPermissionOnFirstPlay(playbackActive = playbackState != null)

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
                    TopLevelDestination.Videos -> VideosScreen(container)
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
            FullPlayerHost(state, controller, container) { showFullPlayer = false }
        }
    }
}

/** Binds the watch view model to the current video and handles end-of-item advance. */
@Composable
private fun BindWatchAndAutoAdvance(
    state: PlaybackState,
    watchViewModel: WatchViewModel,
    container: AppContainer,
) {
    // For YouTube videos the item id IS the video id; bind when it's a video.
    LaunchedEffect(state.itemId, state.hasVideo) {
        if (state.hasVideo) watchViewModel.bind(state.itemId.value)
    }
    // When the current item ends: play the next queued item if there is one;
    // otherwise a video rolls on to the top related ("up next") video as before.
    LaunchedEffect(state.hasEnded) {
        if (state.hasEnded && !container.playbackQueue.playNextInQueue() && state.hasVideo) {
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
    onDismiss: () -> Unit,
) {
    val watchViewModel: WatchViewModel = viewModel(factory = WatchViewModel.factory(container))
    BindWatchAndAutoAdvance(state, watchViewModel, container)
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
