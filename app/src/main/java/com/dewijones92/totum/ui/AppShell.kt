package com.dewijones92.totum.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.data.queue.QueueEntry
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.di.fake.FakeAppContainer
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.navigation.TopLevelDestination
import com.dewijones92.totum.playback.PlaybackController
import com.dewijones92.totum.playback.PlaybackState
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.theme.TotumTheme
import com.dewijones92.totum.ui.channel.ChannelScreen
import com.dewijones92.totum.ui.common.ItemActionSheet
import com.dewijones92.totum.ui.common.MiniPlayerBar
import com.dewijones92.totum.ui.common.ProvidePlayStates
import com.dewijones92.totum.ui.common.RequestNotificationPermissionOnFirstPlay
import com.dewijones92.totum.ui.library.LibraryScreen
import com.dewijones92.totum.ui.player.CommentReplies
import com.dewijones92.totum.ui.player.FullPlayerOverlay
import com.dewijones92.totum.ui.player.LocalVideoBounds
import com.dewijones92.totum.ui.player.PictureInPictureEffect
import com.dewijones92.totum.ui.player.PlaybackToggles
import com.dewijones92.totum.ui.player.QualityControl
import com.dewijones92.totum.ui.player.QueueControls
import com.dewijones92.totum.ui.player.VideoBounds
import com.dewijones92.totum.ui.player.WatchActions
import com.dewijones92.totum.ui.player.WatchViewModel
import com.dewijones92.totum.ui.player.rememberIsInPictureInPicture
import com.dewijones92.totum.ui.podcasts.PodcastsScreen
import com.dewijones92.totum.ui.queue.QueueScreen
import com.dewijones92.totum.ui.search.SearchScreen
import com.dewijones92.totum.ui.shorts.ShortsReelScreen
import com.dewijones92.totum.ui.videos.VideosScreen
import com.dewijones92.totum.video.VideoPlaybackLauncher

/**
 * Top-level scaffold: bottom navigation across the app's pillars with
 * animated transitions between them.
 */
@Composable
fun AppShell(container: AppContainer, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(TopLevelDestination.Videos) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    var shortsReel by remember { mutableStateOf<List<MediaItem>?>(null) }
    // "Go to channel" works from ANY row because the shell hosts the destination once.
    var shellChannel by remember { mutableStateOf<MediaSource.VideoChannel?>(null) }
    val playbackState by container.playbackController.state.collectAsStateWithLifecycle()
    val controller = container.playbackController
    val watchViewModel: WatchViewModel = viewModel(factory = WatchViewModel.factory(container))

    RequestNotificationPermissionOnFirstPlay(playbackActive = playbackState != null)
    // The stage reports where the picture is, so the system animates from it rather
    // than cross-fading the whole app into the floating window.
    val videoBounds = remember { VideoBounds() }
    val inPip = floatingWindowState(playbackState, controller, videoBounds)
    // End-of-item advance lives here, always composed — so the queue advances in the
    // mini player / with the screen off, not only while the full player is expanded.
    AutoAdvance(playbackState, watchViewModel, container, reelOpen = shortsReel != null)

    // A floating window is centimetres across: the nav bar, mini player and scrolling
    // description would leave no room for the picture, so it renders alone.
    if (inPip) {
        FloatingVideo(playbackState, controller.player, modifier)
        return
    }

    CompositionLocalProvider(LocalVideoBounds provides videoBounds) {
        ProvidePlayStates(container, onOpenChannel = { shellChannel = it }) {
            Box(modifier = modifier.fillMaxSize()) {
                Scaffold(
                    bottomBar = {
                        BottomBar(
                            state = playbackState,
                            selected = selected,
                            onTogglePlayPause = controller::togglePlayPause,
                            onExpand = { showFullPlayer = true },
                            onSelect = { selected = it },
                        )
                    },
                ) { innerPadding ->
                    TopLevelContent(
                        container = container,
                        selected = selected,
                        onOpenShorts = { shortsReel = it },
                        modifier = Modifier.padding(innerPadding),
                    )
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
                shellChannel?.let { channel ->
                    ChannelScreen(
                        container,
                        channel,
                        onBack = { shellChannel = null },
                        onOpenPlaylist = {},
                    )
                }
            }
        }
    }
}

/** The mini player sitting above the tabs — one bar, so neither appears without the other. */
@Composable
private fun BottomBar(
    state: PlaybackState?,
    selected: TopLevelDestination,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Column {
        state?.let {
            MiniPlayerBar(state = it, onTogglePlayPause = onTogglePlayPause, onExpand = onExpand)
        }
        TopLevelNavigationBar(
            selected,
            // Logged because a real report could not answer "did this happen when I switched
            // tabs?" — nothing recorded that the user had, so the question was unanswerable.
            onSelect = { destination ->
                Diag.log("nav", "tab $selected -> $destination")
                onSelect(destination)
            },
        )
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
        val ended = state?.takeIf { it.hasEnded } ?: return@LaunchedEffect
        // Every branch says why, because the failure mode is silence: a real report showed
        // an item end with 58 things queued and nothing happen for three minutes, and there
        // was no way to tell which of these four reasons it was.
        val refusal = when {
            reelOpen -> "the shorts reel is open and pages itself"
            !autoPlayNext.autoPlayNext -> "auto-play next is off"
            handledEndFor == ended.itemId -> "already handled this item's end"
            else -> null
        }
        if (refusal != null) {
            Diag.log("advance", "not advancing past ${ended.itemId.value}: $refusal")
            return@LaunchedEffect
        }
        handledEndFor = ended.itemId
        val advanced = container.playbackQueue.playNextInQueue()
        Diag.log("advance", "${ended.itemId.value} ended -> queue advance=$advanced")
        if (!advanced && ended.hasVideo) {
            Diag.log("advance", "queue had nothing playable; trying a related video")
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
        onStopSleepAfterItem = container.sleepTimer::stopAfterCurrentItem,
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

    PlayingItemSheet(playing, showItemSheet) { showItemSheet = false }
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
 * its handle rather than a PlaybackState reconstruction.
 *
 * Every action comes from the app-wide [ItemActions]. It used to re-implement download,
 * mark-played and go-to-channel here, which is the very duplication that made menus differ
 * between screens in the first place.
 */
@Composable
private fun PlayingItemSheet(
    playing: PlayableItem?,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val item = playing?.item ?: return
    if (!visible) return
    ItemActionSheet(item, onDismiss)
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

/**
 * The selected pillar's screen, cross-faded as the bottom navigation changes.
 *
 * Each destination keeps its own state while you are away from it. Switching tabs
 * tears the outgoing screen out of composition, so without this a glance at the queue
 * threw away a long scroll through subscriptions and dropped you back at the top —
 * which made the tabs feel unsafe to use. [rememberSaveableStateHolder] is what a
 * NavHost uses for the same job: state saved under a key per destination, restored when
 * that destination comes back.
 *
 * Applied around the whole `when` rather than per screen on purpose. A tab that has to
 * opt in is a bug waiting for the next tab to be added.
 */
@Composable
private fun TopLevelContent(
    container: AppContainer,
    selected: TopLevelDestination,
    onOpenShorts: (List<MediaItem>) -> Unit,
    modifier: Modifier,
) {
    val stateHolder = rememberSaveableStateHolder()
    AnimatedContent(targetState = selected, modifier = modifier, label = "top-level-destination") { destination ->
        stateHolder.SaveableStateProvider(destination.name) {
            Destination(container, destination, onOpenShorts)
        }
    }
}

/** The one place a destination maps to its screen. */
@Composable
private fun Destination(
    container: AppContainer,
    destination: TopLevelDestination,
    onOpenShorts: (List<MediaItem>) -> Unit,
) {
    when (destination) {
        TopLevelDestination.Videos -> VideosScreen(container, onOpenShorts = onOpenShorts)
        TopLevelDestination.Podcasts -> PodcastsScreen(container)
        TopLevelDestination.Queue -> QueueScreen(container)
        TopLevelDestination.Search -> SearchScreen(container)
        TopLevelDestination.Library -> LibraryScreen(container)
    }
}

/**
 * Publishes picture-in-picture parameters for whatever is playing and reports whether the
 * app is currently floating. Always composed, so it tracks playback rather than only what
 * the full player happens to be showing.
 */
@Composable
private fun floatingWindowState(
    state: PlaybackState?,
    controller: PlaybackController,
    bounds: VideoBounds,
): Boolean {
    PictureInPictureEffect(
        hasVideo = state?.hasVideo == true,
        isPlaying = state?.isPlaying == true,
        aspectRatio = state?.videoAspectRatio,
        bounds = bounds,
        onTogglePlayPause = controller::togglePlayPause,
    )
    return rememberIsInPictureInPicture()
}

/**
 * The picture, alone, for the floating window — no chrome, no controls. PiP supplies its
 * own play/pause action in the window frame, so drawing our own would only cover video
 * that has very little room to begin with.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FloatingVideo(state: PlaybackState?, player: androidx.media3.common.Player?, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (player != null && state?.hasVideo == true) {
            androidx.media3.ui.compose.PlayerSurface(
                player = player,
                surfaceType = androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
