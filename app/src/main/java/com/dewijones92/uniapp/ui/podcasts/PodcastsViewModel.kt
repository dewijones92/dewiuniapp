package com.dewijones92.uniapp.ui.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.SubscribeResult
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PodcastsViewModel(private val repository: PodcastRepository) : ViewModel() {

    data class UiState(
        val subscriptions: List<Subscription> = emptyList(),
        val episodes: List<MediaItem> = emptyList(),
        val subscribing: Subscribing = Subscribing.Idle,
    )

    /** State of the current subscribe attempt; the dialog renders from this. */
    sealed interface Subscribing {
        data object Idle : Subscribing
        data object InProgress : Subscribing
        data object Done : Subscribing

        sealed interface Error : Subscribing {
            data object InvalidUrl : Error
            data object Network : Error
            data object InvalidFeed : Error
            data object AlreadySubscribed : Error
        }
    }

    private val subscribing = MutableStateFlow<Subscribing>(Subscribing.Idle)

    val uiState: StateFlow<UiState> = combine(
        repository.observeSubscriptions(),
        repository.observeEpisodes(),
        subscribing,
        ::UiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun subscribe(rawUrl: String) {
        val url = HttpUrl.parse(rawUrl)
        if (url == null) {
            subscribing.value = Subscribing.Error.InvalidUrl
            return
        }
        viewModelScope.launch {
            subscribing.value = Subscribing.InProgress
            subscribing.value = when (repository.subscribe(url)) {
                is SubscribeResult.Subscribed -> Subscribing.Done
                is SubscribeResult.AlreadySubscribed -> Subscribing.Error.AlreadySubscribed
                is SubscribeResult.Failure.Network -> Subscribing.Error.Network
                is SubscribeResult.Failure.InvalidFeed -> Subscribing.Error.InvalidFeed
            }
        }
    }

    /** Call when the add-podcast dialog closes, so the next attempt starts clean. */
    fun resetSubscribing() {
        subscribing.update { Subscribing.Idle }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(repository: PodcastRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { PodcastsViewModel(repository) }
        }
    }
}
