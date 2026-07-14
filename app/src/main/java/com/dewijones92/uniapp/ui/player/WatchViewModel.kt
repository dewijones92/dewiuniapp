package com.dewijones92.uniapp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.actions.ActionResult
import com.dewijones92.uniapp.innertube.actions.VideoRating
import com.dewijones92.uniapp.innertube.actions.YouTubeActions
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.comments.Comment
import com.dewijones92.uniapp.innertube.comments.CommentsResult
import com.dewijones92.uniapp.innertube.comments.YouTubeComments
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.related.RelatedResult
import com.dewijones92.uniapp.innertube.related.YouTubeRelated
import com.dewijones92.uniapp.video.VideoPlaybackLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the full player's watch page for one video: reads comments (public),
 * and — when signed in — likes and posts comments. Keyed by video id, so
 * switching videos resets. Writes are optimistic where it helps responsiveness
 * but always reflect the real result.
 */
class WatchViewModel(
    private val commentsSource: YouTubeComments,
    private val relatedSource: YouTubeRelated,
    private val actions: YouTubeActions,
    private val account: YouTubeAccount,
    private val launcher: VideoPlaybackLauncher,
) : ViewModel() {

    /** The current video's selectable qualities; switching replays from the same spot. */
    val quality: StateFlow<VideoPlaybackLauncher.QualityState> = launcher.quality

    fun selectQuality(id: String): Unit = launcher.selectQuality(id)

    sealed interface CommentsState {
        data object Loading : CommentsState
        data class Loaded(val comments: List<Comment>) : CommentsState
        data object Disabled : CommentsState
        data object Error : CommentsState
    }

    enum class PostState { Idle, Posting, Posted, Failed }

    sealed interface RelatedState {
        data object Loading : RelatedState
        data class Loaded(val videos: List<FeedVideo>) : RelatedState
        data object Error : RelatedState
    }

    private val _comments = MutableStateFlow<CommentsState>(CommentsState.Loading)
    val comments: StateFlow<CommentsState> get() = _comments.asStateFlow()

    private val _related = MutableStateFlow<RelatedState>(RelatedState.Loading)
    val related: StateFlow<RelatedState> get() = _related.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _rating = MutableStateFlow(VideoRating.NONE)
    val rating: StateFlow<VideoRating> = _rating.asStateFlow()

    private val _postState = MutableStateFlow(PostState.Idle)
    val postState: StateFlow<PostState> = _postState.asStateFlow()

    private var videoId: String? = null

    fun bind(videoId: String) {
        if (videoId == this.videoId) return
        this.videoId = videoId
        _comments.value = CommentsState.Loading
        _related.value = RelatedState.Loading
        _rating.value = VideoRating.NONE
        _postState.value = PostState.Idle
        viewModelScope.launch { _signedIn.value = account.isSignedIn() }
        viewModelScope.launch {
            _comments.value = when (val result = commentsSource.forVideo(videoId)) {
                is CommentsResult.Success -> CommentsState.Loaded(result.comments)
                CommentsResult.Disabled -> CommentsState.Disabled
                is CommentsResult.Failure -> CommentsState.Error
            }
        }
        viewModelScope.launch {
            _related.value = when (val result = relatedSource.relatedTo(videoId)) {
                is RelatedResult.Success -> RelatedState.Loaded(result.videos)
                is RelatedResult.Failure -> RelatedState.Error
            }
        }
    }

    /** Plays a tapped related video through the one launcher. */
    fun playRelated(video: FeedVideo) {
        viewModelScope.launch { launcher.play(video.watchUrl, RELATED_SOURCE) }
    }

    /**
     * Plays the next up-next video when the current one ends — the top related
     * that isn't the video just watched. No-op if related hasn't loaded or is
     * empty.
     */
    fun autoplayNext() {
        val videos = (_related.value as? RelatedState.Loaded)?.videos ?: return
        val next = videos.firstOrNull { it.videoId != videoId } ?: return
        playRelated(next)
    }

    /** Toggles like on/off (YouTube clears any dislike when you like). */
    fun toggleLike() {
        setRating(if (_rating.value == VideoRating.LIKE) VideoRating.NONE else VideoRating.LIKE)
    }

    /** Toggles dislike on/off (YouTube clears any like when you dislike). */
    fun toggleDislike() {
        setRating(if (_rating.value == VideoRating.DISLIKE) VideoRating.NONE else VideoRating.DISLIKE)
    }

    private fun setRating(target: VideoRating) {
        val id = videoId ?: return
        val previous = _rating.value
        _rating.value = target // optimistic
        viewModelScope.launch {
            if (actions.setRating(id, target) !is ActionResult.Success) _rating.value = previous
        }
    }

    fun postComment(text: String) {
        val id = videoId ?: return
        if (text.isBlank() || _postState.value == PostState.Posting) return
        _postState.value = PostState.Posting
        viewModelScope.launch {
            _postState.value = if (actions.postComment(id, text.trim()) is ActionResult.Success) {
                PostState.Posted
            } else {
                PostState.Failed
            }
        }
    }

    fun clearPostState() = _postState.update { PostState.Idle }

    companion object {
        /** Ad-hoc source for a related video — not tied to a subscribed channel or feed. */
        private val RELATED_SOURCE = SourceId("watch:related-video")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WatchViewModel(
                    container.youTubeComments,
                    container.youTubeRelated,
                    container.youTubeActions,
                    container.youTubeAccount,
                    container.videoPlaybackLauncher,
                )
            }
        }
    }
}
