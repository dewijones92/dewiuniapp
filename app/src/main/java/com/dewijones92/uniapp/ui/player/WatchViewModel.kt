package com.dewijones92.uniapp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.innertube.actions.ActionResult
import com.dewijones92.uniapp.innertube.actions.YouTubeActions
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.comments.Comment
import com.dewijones92.uniapp.innertube.comments.CommentsResult
import com.dewijones92.uniapp.innertube.comments.YouTubeComments
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

    private val _comments = MutableStateFlow<CommentsState>(CommentsState.Loading)
    val comments: StateFlow<CommentsState> get() = _comments.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked.asStateFlow()

    private val _postState = MutableStateFlow(PostState.Idle)
    val postState: StateFlow<PostState> = _postState.asStateFlow()

    private var videoId: String? = null

    fun bind(videoId: String) {
        if (videoId == this.videoId) return
        this.videoId = videoId
        _comments.value = CommentsState.Loading
        _liked.value = false
        _postState.value = PostState.Idle
        viewModelScope.launch { _signedIn.value = account.isSignedIn() }
        viewModelScope.launch {
            _comments.value = when (val result = commentsSource.forVideo(videoId)) {
                is CommentsResult.Success -> CommentsState.Loaded(result.comments)
                CommentsResult.Disabled -> CommentsState.Disabled
                is CommentsResult.Failure -> CommentsState.Error
            }
        }
    }

    fun toggleLike() {
        val id = videoId ?: return
        val target = !_liked.value
        _liked.value = target // optimistic
        viewModelScope.launch {
            if (actions.setLiked(id, target) !is ActionResult.Success) _liked.value = !target
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
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WatchViewModel(
                    container.youTubeComments,
                    container.youTubeActions,
                    container.youTubeAccount,
                    container.videoPlaybackLauncher,
                )
            }
        }
    }
}
