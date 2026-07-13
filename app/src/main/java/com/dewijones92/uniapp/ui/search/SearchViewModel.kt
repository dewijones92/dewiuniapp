package com.dewijones92.uniapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.search.SearchHit
import com.dewijones92.uniapp.data.search.SearchOutcome
import com.dewijones92.uniapp.data.search.SearchQuery
import com.dewijones92.uniapp.data.search.SearchSource
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.video.VideoResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val podcastSearch: SearchSource,
    private val videoSearch: SearchSource,
    private val podcastRepository: PodcastRepository,
    private val playback: PlaybackController,
    private val resolver: VideoResolver,
) : ViewModel() {

    data class UiState(
        val results: Results = Results.Idle,
        /** Feed URLs already subscribed, so podcast hits render as such. */
        val subscribedFeeds: Set<String> = emptySet(),
        /** Watch URL currently being resolved for playback, if any. */
        val resolving: String? = null,
        val resolveFailed: Boolean = false,
    )

    sealed interface Results {
        data object Idle : Results
        data object Searching : Results

        /** Sections are independent: one backend failing doesn't hide the other. */
        data class Loaded(
            val podcasts: List<SearchHit.Podcast>,
            val videos: List<SearchHit.Video>,
            val podcastsFailed: Boolean,
            val videosFailed: Boolean,
        ) : Results
    }

    private val results = MutableStateFlow<Results>(Results.Idle)
    private val playAttempt = MutableStateFlow(PlayAttempt())

    private data class PlayAttempt(val resolving: String? = null, val failed: Boolean = false)

    val uiState: StateFlow<UiState> = combine(
        results,
        podcastRepository.observeSubscriptions().map { subscriptions ->
            subscriptions.mapNotNullTo(mutableSetOf()) {
                (it.source as? MediaSource.PodcastFeed)?.feedUrl?.value
            }
        },
        playAttempt,
    ) { results, subscribed, attempt ->
        UiState(results, subscribed, attempt.resolving, attempt.failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    fun search(rawQuery: String) {
        val query = rawQuery.trim().takeIf { it.isNotBlank() }?.let(::SearchQuery) ?: return
        viewModelScope.launch {
            results.value = Results.Searching
            val podcasts = async { podcastSearch.search(query, RESULTS_PER_SECTION) }
            val videos = async { videoSearch.search(query, RESULTS_PER_SECTION) }
            results.value = toLoaded(podcasts.await(), videos.await())
        }
    }

    fun subscribe(hit: SearchHit.Podcast) {
        viewModelScope.launch {
            // Outcome surfaces via observeSubscriptions; failures leave the button active.
            podcastRepository.subscribe(hit.feedUrl)
        }
    }

    /** Resolves the hit's stream (shared [VideoResolver]) and hands it to the shared player. */
    fun playVideo(hit: SearchHit.Video) {
        viewModelScope.launch {
            playAttempt.value = PlayAttempt(resolving = hit.watchUrl.value)
            val resolved = resolver.resolve(hit.watchUrl, AD_HOC_VIDEO_SOURCE)
            if (resolved == null) {
                playAttempt.value = PlayAttempt(failed = true)
                return@launch
            }
            playback.play(resolved.item, skipSegments = resolved.skipSegments)
            playAttempt.value = PlayAttempt()
        }
    }

    private fun toLoaded(podcasts: SearchOutcome, videos: SearchOutcome) = Results.Loaded(
        podcasts = (podcasts as? SearchOutcome.Success)?.hits?.filterIsInstance<SearchHit.Podcast>().orEmpty(),
        videos = (videos as? SearchOutcome.Success)?.hits?.filterIsInstance<SearchHit.Video>().orEmpty(),
        podcastsFailed = podcasts is SearchOutcome.Failure,
        videosFailed = videos is SearchOutcome.Failure,
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val RESULTS_PER_SECTION = 8

        /** Ad-hoc plays from search don't belong to a subscribed source yet. */
        private val AD_HOC_VIDEO_SOURCE = SourceId("search:ad-hoc-video")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    podcastSearch = container.podcastSearchSource,
                    videoSearch = container.videoSearchSource,
                    podcastRepository = container.podcastRepository,
                    playback = container.playbackController,
                    resolver = container.videoResolver,
                )
            }
        }
    }
}
