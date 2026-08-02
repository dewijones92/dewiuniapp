package com.dewijones92.totum.ui.search

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.Page
import com.dewijones92.totum.common.append
import com.dewijones92.totum.data.podcast.PodcastRepository
import com.dewijones92.totum.data.queue.QueueGroup
import com.dewijones92.totum.data.search.SearchHistoryStore
import com.dewijones92.totum.data.search.SearchHit
import com.dewijones92.totum.data.search.SearchOutcome
import com.dewijones92.totum.data.search.SearchQuery
import com.dewijones92.totum.data.search.SearchSource
import com.dewijones92.totum.data.torrent.HomeTorrentServer
import com.dewijones92.totum.data.torrent.TorrentEpisodes
import com.dewijones92.totum.data.torrent.TorrentPlayables
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.ui.common.TrackedViewModel
import com.dewijones92.totum.ui.common.toMediaItem
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

/**
 * The two halves of the home-server feature, together because neither is useful alone: search
 * with no server cannot play what it finds, and a server with no search has nothing to play.
 * Grouping them also keeps the view model's dependencies down to the things it actually has.
 */
class TorrentServices(val search: SearchSource, val server: HomeTorrentServer) {
    companion object {
        /** Null unless BOTH are configured, so a half-set-up server is absent rather than odd. */
        fun from(container: AppContainer): TorrentServices? {
            val search = container.torrentSearchSource ?: return null
            val server = container.homeTorrentServer ?: return null
            return TorrentServices(search, server)
        }
    }
}

@Suppress("TooManyFunctions") // One method per user action on a screen with several sections.
class SearchViewModel(
    private val podcastSearch: SearchSource,
    private val videoSearch: SearchSource,
    /** Null when no home server is configured — the section is then absent, not broken. */
    private val torrents: TorrentServices?,
    private val podcastRepository: PodcastRepository,
    private val queue: PlaybackQueue,
    private val history: SearchHistoryStore,
) : TrackedViewModel("search") {

    data class UiState(
        val results: Results = Results.Idle,
        /** Feed URLs already subscribed, so podcast hits render as such. */
        val subscribedFeeds: Set<String> = emptySet(),
        /** Recent searches, offered in the idle state. */
        val history: List<String> = emptyList(),
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
            /** Carries its own continuation, so the section knows whether more exists. */
            val videos: Page<SearchHit.Video>,
            /** Empty when no home server is set up, which is not a failure. */
            val torrents: List<SearchHit.Torrent>,
            val podcastsFailed: Boolean,
            val videosFailed: Boolean,
            /** Distinct from empty: the Pi is only reachable at home or on wg-home. */
            val torrentsFailed: Boolean,
            val loadingMore: Boolean = false,
        ) : Results {
            val canLoadMore: Boolean get() = videos.hasMore
        }
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
    private val searched: Flow<Results> = typed
        .debounce(DEBOUNCE_MILLIS)
        .map { it.trim() }
        .distinctUntilChanged()
        .transformLatest { raw ->
            if (raw.length < MIN_QUERY_LENGTH) {
                activeQuery = null
                emit(Results.Idle)
            } else {
                emit(Results.Searching)
                val query = SearchQuery(raw)
                activeQuery = query
                emit(runSearch(query))
            }
        }

    /*
     * Held rather than derived, because "load more" appends to what is already on screen
     * — a pure derivation of the query text has nowhere to put page two. The typed flow
     * feeds this; loadMoreVideos appends to it.
     */
    private val results = MutableStateFlow<Results>(Results.Idle)

    /** The query the current results belong to, so a continuation asks about the right one. */
    private var activeQuery: SearchQuery? = null

    init {
        viewModelScope.launch { searched.collect { results.value = it } }
    }

    val uiState: StateFlow<UiState> = combine(
        results,
        podcastRepository.observeSubscriptions().map { subscriptions ->
            subscriptions.mapNotNullTo(mutableSetOf()) {
                (it.source as? MediaSource.PodcastFeed)?.feedUrl?.value
            }
        },
        playAttempt,
        history.recent(),
    ) { results, subscribed, attempt, recent ->
        UiState(results, subscribed, recent, attempt.resolving, attempt.failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState())

    /** Called on every keystroke; the debounce lives in the results flow. */
    fun onQueryChange(rawQuery: String) {
        typed.value = rawQuery
    }

    /** Explicit submit (search button / IME action / history tap); records to history. */
    fun search(rawQuery: String) {
        typed.value = rawQuery
        val trimmed = rawQuery.trim()
        if (trimmed.length >= MIN_QUERY_LENGTH) {
            viewModelScope.launch { history.record(trimmed) }
        }
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { history.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    private suspend fun runSearch(query: SearchQuery): Results = coroutineScope {
        val podcasts = async { podcastSearch.search(query, RESULTS_PER_SECTION, after = null) }
        val videos = async { videoSearch.search(query, RESULTS_PER_SECTION, after = null) }
        // Independent of the others, like every section: the home server being unreachable must
        // not hide YouTube results, and a YouTube failure must not hide torrents.
        val torrents = async { this@SearchViewModel.torrents?.search?.search(query, RESULTS_PER_SECTION, null) }
        toLoaded(podcasts.await(), videos.await(), torrents.await()).also {
            Diag.log(
                "search",
                "\"${query.value}\" -> ${it.podcasts.size} podcasts, " +
                    "${it.videos.items.size} videos, ${it.torrents.size} torrents " +
                    "(more=${it.canLoadMore})",
            )
        }
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
            val played = queue.playNow(
                PlayableItem(hit.toMediaItem(AD_HOC_VIDEO_SOURCE), PlayHandle.Video(hit.watchUrl)),
            )
            playAttempt.value = if (played) PlayAttempt() else PlayAttempt(failed = true)
        }
    }

    private fun toLoaded(
        podcasts: SearchOutcome,
        videos: SearchOutcome,
        torrents: SearchOutcome?,
    ) = Results.Loaded(
        podcasts = (podcasts as? SearchOutcome.Success)
            ?.page?.items?.filterIsInstance<SearchHit.Podcast>().orEmpty(),
        videos = (videos as? SearchOutcome.Success)?.page?.videosOnly() ?: Page.empty(),
        torrents = (torrents as? SearchOutcome.Success)
            ?.page?.items?.filterIsInstance<SearchHit.Torrent>().orEmpty(),
        podcastsFailed = podcasts is SearchOutcome.Failure,
        videosFailed = videos is SearchOutcome.Failure,
        torrentsFailed = torrents is SearchOutcome.Failure,
    )

    /**
     * Adds a torrent to the home server and queues everything playable in it.
     *
     * A season pack becomes one queue item per episode, which is why this is `playAll` with a
     * group rather than `playNow` with one thing — the queue then shows a header for the release
     * and can remove the whole season as a unit, exactly as it already does for a playlist.
     *
     * Preparing is the slow part (the server has to reach the swarm and read the metadata), so
     * the UI is told it is working rather than left silent for several seconds.
     */
    fun playTorrent(hit: SearchHit.Torrent) {
        val server = torrents?.server ?: return
        viewModelScope.launch {
            playAttempt.value = PlayAttempt(resolving = hit.title)
            val prepared = server.prepare(hit.magnet)
            val items = prepared?.let { TorrentPlayables.queueItems(server, it) }.orEmpty()
            if (items.isEmpty()) {
                Diag.warn("search", "\"${hit.title}\" had nothing playable in it")
                playAttempt.value = PlayAttempt(failed = true)
                return@launch
            }
            Diag.log("search", "queueing ${items.size} item(s) from \"${hit.title}\"")
            // Start the audio remux for what is about to play, without waiting for it. The
            // first HLS segment takes ~25s while ffmpeg waits on the swarm, so if this is left
            // until Listen is pressed it is 25 seconds of spinner; started here it overlaps the
            // queueing and the video that plays first.
            TorrentEpisodes.playableInOrder(prepared!!.files).firstOrNull()?.let { first ->
                launch { server.warmAudio(prepared, first) }
            }
            queue.playAll(items, QueueGroup(id = prepared.hash, title = prepared.name))
            playAttempt.value = PlayAttempt()
        }
    }

    /**
     * Fetches the next page of video results and appends it.
     *
     * Podcasts have no equivalent: the directory answers in one shot, which its source
     * states by returning a final page — so there is simply never a token to follow.
     */
    fun loadMoreVideos() {
        val current = results.value as? Results.Loaded ?: return
        if (current.loadingMore) return
        val token = current.videos.next ?: return
        val query = activeQuery ?: return
        results.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val outcome = videoSearch.search(query, RESULTS_PER_SECTION, token)
            // Re-read: a new query may have landed while this page was in flight, and
            // appending page two of the old search to it would be worse than dropping it.
            val latest = results.value as? Results.Loaded ?: return@launch
            if (activeQuery != query) return@launch
            results.value = when (outcome) {
                is SearchOutcome.Success -> {
                    val grown = latest.videos.append(outcome.page.videosOnly()) { it.watchUrl.value }
                    Diag.log(
                        "search",
                        "next page -> ${outcome.page.items.size} returned, " +
                            "${grown.items.size} total (more=${grown.hasMore})",
                    )
                    latest.copy(videos = grown, loadingMore = false)
                }
                is SearchOutcome.Failure -> {
                    Diag.warn("search", "next page failed: ${outcome.detail}")
                    latest.copy(loadingMore = false)
                }
            }
        }
    }

    private fun Page<SearchHit>.videosOnly(): Page<SearchHit.Video> =
        Page(items.filterIsInstance<SearchHit.Video>(), next)

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val RESULTS_PER_SECTION = 8
        private const val DEBOUNCE_MILLIS = 300L
        private const val MIN_QUERY_LENGTH = 2

        /** Ad-hoc plays from search don't belong to a subscribed source yet. */
        /** Shared with the search row, which builds the same MediaItem for its actions. */
        internal val AD_HOC_VIDEO_SOURCE = SourceId("search:ad-hoc-video")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    podcastSearch = container.podcastSearchSource,
                    videoSearch = container.videoSearchSource,
                    torrents = TorrentServices.from(container),
                    podcastRepository = container.podcastRepository,
                    queue = container.playbackQueue,
                    history = container.searchHistoryStore,
                )
            }
        }
    }
}
