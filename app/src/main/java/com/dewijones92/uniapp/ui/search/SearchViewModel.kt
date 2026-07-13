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
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class SearchViewModel(
    private val podcastSearch: SearchSource,
    private val videoSearch: SearchSource,
    private val podcastRepository: PodcastRepository,
    private val launcher: VideoPlaybackLauncher,
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

    private val playAttempt = MutableStateFlow(PlayAttempt())

    private data class PlayAttempt(val resolving: String? = null, val failed: Boolean = false)

    /** The current query text; every keystroke and explicit submit sets it. */
    private val typed = MutableStateFlow("")

    /**
     * The one search stream driving search-as-you-type: typing is debounced,
     * [distinctUntilChanged] avoids re-running an unchanged query, and
     * [transformLatest] cancels any in-flight search when the query changes.
     * Below [MIN_QUERY_LENGTH] the results reset to Idle rather than hammering
     * the backends on a single keystroke.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val results: Flow<Results> = typed
        .debounce(DEBOUNCE_MILLIS)
        .map { it.trim() }
        .distinctUntilChanged()
        .transformLatest { raw ->
            if (raw.length < MIN_QUERY_LENGTH) {
                emit(Results.Idle)
            } else {
                emit(Results.Searching)
                emit(runSearch(SearchQuery(raw)))
            }
        }

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

    /** Called on every keystroke; the debounce lives in the results flow. */
    fun onQueryChange(rawQuery: String) {
        typed.value = rawQuery
    }

    /** Explicit submit (search button / IME action); the query text drives the same stream. */
    fun search(rawQuery: String) {
        typed.value = rawQuery
    }

    private suspend fun runSearch(query: SearchQuery): Results = coroutineScope {
        val podcasts = async { podcastSearch.search(query, RESULTS_PER_SECTION) }
        val videos = async { videoSearch.search(query, RESULTS_PER_SECTION) }
        toLoaded(podcasts.await(), videos.await())
    }

    fun subscribe(hit: SearchHit.Podcast) {
        viewModelScope.launch {
            // Outcome surfaces via observeSubscriptions; failures leave the button active.
            podcastRepository.subscribe(hit.feedUrl)
        }
    }

    /** Resolves the hit's stream (shared launcher) and hands it to the shared player. */
    fun playVideo(hit: SearchHit.Video) {
        viewModelScope.launch {
            playAttempt.value = PlayAttempt(resolving = hit.watchUrl.value)
            val played = launcher.play(hit.watchUrl, AD_HOC_VIDEO_SOURCE)
            playAttempt.value = if (played) PlayAttempt() else PlayAttempt(failed = true)
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
        private const val DEBOUNCE_MILLIS = 300L
        private const val MIN_QUERY_LENGTH = 2

        /** Ad-hoc plays from search don't belong to a subscribed source yet. */
        private val AD_HOC_VIDEO_SOURCE = SourceId("search:ad-hoc-video")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    podcastSearch = container.podcastSearchSource,
                    videoSearch = container.videoSearchSource,
                    podcastRepository = container.podcastRepository,
                    launcher = container.videoPlaybackLauncher,
                )
            }
        }
    }
}
