package com.dewijones92.uniapp.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.feeds.FeedResult
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds
import com.dewijones92.uniapp.notifications.NewUploadsTracker
import com.dewijones92.uniapp.ui.common.toMediaItem
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The new-uploads "bell": how many subscription videos have appeared since the
 * user last looked, and the list of them. Stands in for YouTube's real
 * notification feed (unreachable with this app's token).
 */
class NotificationsViewModel(
    private val feeds: YouTubeFeeds,
    private val tracker: NewUploadsTracker,
    private val launcher: VideoPlaybackLauncher,
) : ViewModel() {

    private var lastFeed: List<FeedVideo> = emptyList()
    private val newUploads = MutableStateFlow<List<FeedVideo>>(emptyList())

    val count: StateFlow<Int> = newUploads
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    /** The current new uploads as media items — read on demand (always current). */
    fun snapshotUploads(): List<MediaItem> = newUploads.value.map { it.toMediaItem(SOURCE_ID) }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val feed = (feeds.subscriptionsFeed() as? FeedResult.Success)?.videos ?: return@launch
            lastFeed = feed
            newUploads.value = tracker.newUploads(feed)
        }
    }

    /** Called when the user opens the list — everything current becomes "seen". */
    fun markAllSeen() {
        tracker.markSeen(lastFeed)
        newUploads.value = emptyList()
    }

    fun play(video: MediaItem) {
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch { launcher.play(watchUrl, video.sourceId) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private val SOURCE_ID = SourceId("ytnotif")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotificationsViewModel(
                    feeds = container.youTubeFeeds,
                    tracker = container.newUploadsTracker,
                    launcher = container.videoPlaybackLauncher,
                )
            }
        }
    }
}
