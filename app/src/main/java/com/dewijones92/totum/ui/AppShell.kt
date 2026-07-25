package com.dewijones92.totum.ui

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.totum.R
import com.dewijones92.totum.data.queue.QueueEntry
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.di.fake.FakeAppContainer
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.navigation.TopLevelDestination
import com.dewijones92.totum.playback.PlaybackController
import com.dewijones92.totum.playback.PlaybackState
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.theme.TotumTheme
import com.dewijones92.totum.ui.common.ActionSheet
import com.dewijones92.totum.ui.common.MiniPlayerBar
import com.dewijones92.totum.ui.common.ProvidePlayStates
import com.dewijones92.totum.ui.common.RequestNotificationPermissionOnFirstPlay
import com.dewijones92.totum.ui.common.rememberMediaItemActions
import com.dewijones92.totum.ui.library.LibraryScreen
import com.dewijones92.totum.ui.player.CommentReplies
import com.dewijones92.totum.ui.player.FullPlayerOverlay
import com.dewijones92.totum.ui.player.PlaybackToggles
import com.dewijones92.totum.ui.player.QualityControl
import com.dewijones92.totum.ui.player.QueueControls
import com.dewijones92.totum.ui.player.WatchActions
import com.dewijones92.totum.ui.player.WatchViewModel
import com.dewijones92.totum.ui.podcasts.PodcastsScreen
import com.dewijones92.totum.ui.queue.QueueScreen
import com.dewijones92.totum.ui.search.SearchScreen
import com.dewijones92.totum.ui.shorts.ShortsReelScreen
import com.dewijones92.totum.ui.videos.VideosScreen
import com.dewijones92.totum.video.VideoPlaybackLauncher
import kotlinx.coroutines.launch

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

    ProvidePlayStates(container) {
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
                        TopLevelNavigationBar(selected, onSelect = { selected = it })
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
                        TopLevelDestination.Queue -> QueueScreen(container)
                        TopLevelDestination.Search -> SearchScreen(container)
                        TopLevelDestination.Library -> LibraryScreen(container)
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
}

@Composable
private fun TopLevelNavigationBar(selected: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val isSelected = destination == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    val icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon
                    Icon(imageVector = icon, contentDescription = null)
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
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
    val autoPlayNext by container.appPreferences.settings.collectAsStateWithLifecycle()
    var handledEndFor by remember { mutableStateOf(state?.takeIf { it.hasEnded }?.itemId) }
    LaunchedEffect(state?.itemId, state?.hasEnded) {
        if (reelOpen || !autoPlayNext.autoPlayNext) return@LaunchedEffect
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
    val queueState by container.playbackQueue.state.collectAsStateWithLifecycle()
    val upNext = queueState.upNext
    val rowActions = rememberMediaItemActions(container)
    var showItemSheet by remember { mutableStateOf(false) }
    val playing = queueState.current?.item
    val currentIndex = queueState.currentIndex
    val settings by container.appPreferences.settings.collectAsStateWithLifecycle()

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
        quality = qualityControl(quality, watchViewModel),
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
        onSetSubtitleLanguage = controller::setSubtitleLanguage,
        toggles = playbackToggles(state, controller, container, settings.autoPlayNext),
        queue = upNextControls(container.playbackQueue, upNext, currentIndex),
        onMore = { showItemSheet = true }.takeIf { playing != null },
    )

    PlayingItemSheet(container, playing, showItemSheet) { showItemSheet = false }
}

/**
 * The player's up-next list shows what follows the cursor, so its indices are offset from
 * the queue's own — done here once rather than inline at the call site.
 */
private fun upNextControls(queue: PlaybackQueue, upNext: List<QueueEntry>, currentIndex: Int) =
    QueueControls(
        upNext = upNext,
        onPlay = { i -> queue.jumpTo(currentIndex + 1 + i) },
        onRemove = { i -> queue.removeAt(currentIndex + 1 + i) },
    )

/**
 * The SAME sheet the rows use, for whatever is playing — so the player can never offer less
 * than a long-press does. Wired to the current QUEUE entry, which carries the real item and
 * its handle, rather than to a PlaybackState reconstruction.
 */
@Composable
private fun PlayingItemSheet(
    container: AppContainer,
    playing: PlayableItem?,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val item = playing?.item ?: return
    if (!visible) return
    val rowActions = rememberMediaItemActions(container)
    val scope = rememberCoroutineScope()
    val downloads by container.downloadManager.observeDownloads().collectAsStateWithLifecycle(emptyMap())
    val playStates by remember { container.playbackProgressStore.observeStates() }
        .collectAsStateWithLifecycle(emptyMap())
    val local = downloads[item.id]
    ActionSheet(
        title = item.title,
        onPlayNext = { rowActions.playNext(item) },
        onAddToQueue = { rowActions.addToQueue(item) },
        onAddToPlaylist = { rowActions.addToPlaylist(item) },
        onRemoveFromPlaylist = null,
        onPeek = { rowActions.peek(item) },
        onDownloadVideo = {
            scope.launch { container.downloadManager.download(item, audioOnly = false) }
            Unit
        }.takeIf { (local as? DownloadState.Downloaded)?.audioOnly == true },
        onDownload = {
            scope.launch { container.downloadManager.download(item, audioOnly = true) }
            Unit
        }.takeIf { local !is DownloadState.Downloaded && local !is DownloadState.Downloading },
        onSwitchMode = null,
        audioMode = rowActions.audioMode,
        onGoToSource = { rowActions.goToSource(item) { } },
        goToSourceLabelRes = R.string.go_to_channel,
        onMoveToTop = null,
        onMoveToBottom = null,
        onSetPlayed = { played -> scope.launch { container.playbackProgressStore.setPlayed(item.id, played) } },
        played = playStates[item.id]?.isPlayed == true,
        onDismiss = onDismiss,
    )
}

private fun qualityControl(
    quality: VideoPlaybackLauncher.QualityState,
    watchViewModel: WatchViewModel,
) = QualityControl(
    options = quality.options,
    selectedId = quality.selectedId,
    onSelect = watchViewModel::selectQuality,
    canListen = quality.canListen,
    listening = quality.listening,
    onListen = watchViewModel::listen,
    onWatch = watchViewModel::watch,
)

private fun playbackToggles(
    state: PlaybackState,
    controller: PlaybackController,
    container: AppContainer,
    autoPlayNext: Boolean,
) = PlaybackToggles(
    skipSilence = state.skipSilence,
    onSetSkipSilence = controller::setSkipSilence,
    autoPlayNext = autoPlayNext,
    onSetAutoPlayNext = container.appPreferences::setAutoPlayNext,
    onSetVolumeBoost = controller::setVolumeBoost,
)

@Preview(showBackground = true)
@Composable
private fun AppShellPreview() {
    TotumTheme { AppShell(FakeAppContainer()) }
}
