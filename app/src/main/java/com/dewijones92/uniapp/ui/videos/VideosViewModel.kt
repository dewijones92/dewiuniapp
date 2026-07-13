package com.dewijones92.uniapp.ui.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.SubscribeChannelResult
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.video.VideoResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VideosViewModel(
    private val channels: ChannelRepository,
    private val playback: PlaybackController,
    private val resolver: VideoResolver,
) : ViewModel() {

    data class UiState(
        val subscriptions: List<Subscription> = emptyList(),
        val videos: List<MediaItem> = emptyList(),
        val subscribing: Subscribing = Subscribing.Idle,
        /** watchUrl currently being resolved for playback, if any. */
        val resolving: String? = null,
    )

    sealed interface Subscribing {
        data object Idle : Subscribing
        data object InProgress : Subscribing
        data object Done : Subscribing

        sealed interface Error : Subscribing {
            data object InvalidUrl : Error
            data object Network : Error
            data object NotAChannel : Error
            data object AlreadySubscribed : Error
        }
    }

    private val subscribing = MutableStateFlow<Subscribing>(Subscribing.Idle)
    private val resolving = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        channels.observeSubscriptions(),
        channels.observeVideos(),
        subscribing,
        resolving,
    ) { subs, videos, subscribing, resolving ->
        UiState(subs, videos, subscribing, resolving)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun subscribe(rawUrl: String) {
        val url = HttpUrl.parse(rawUrl)
        if (url == null) {
            subscribing.value = Subscribing.Error.InvalidUrl
            return
        }
        viewModelScope.launch {
            subscribing.value = Subscribing.InProgress
            subscribing.value = when (channels.subscribe(url)) {
                is SubscribeChannelResult.Subscribed -> Subscribing.Done
                is SubscribeChannelResult.AlreadySubscribed -> Subscribing.Error.AlreadySubscribed
                is SubscribeChannelResult.Failure.Network -> Subscribing.Error.Network
                is SubscribeChannelResult.Failure.NotAChannel -> Subscribing.Error.NotAChannel
            }
        }
    }

    fun resetSubscribing() {
        subscribing.update { Subscribing.Idle }
    }

    /** Resolves the channel video's stream, then plays it in the shared player. */
    fun play(video: MediaItem) {
        val watchUrl = video.mediaUrl ?: return
        viewModelScope.launch {
            resolving.value = watchUrl.value
            val resolved = resolver.resolve(watchUrl, video.sourceId)
            if (resolved != null) {
                playback.play(resolved.item, skipSegments = resolved.skipSegments)
            }
            resolving.value = null
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VideosViewModel(
                    channels = container.channelRepository,
                    playback = container.playbackController,
                    resolver = container.videoResolver,
                )
            }
        }
    }
}
