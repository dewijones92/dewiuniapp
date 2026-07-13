package com.dewijones92.uniapp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.innertube.comments.Comment
import com.dewijones92.uniapp.innertube.comments.CommentsResult
import com.dewijones92.uniapp.innertube.comments.YouTubeComments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads a video's comments for the full player. Keyed by video id so switching
 * videos reloads; reading needs no sign-in.
 */
class CommentsViewModel(private val comments: YouTubeComments) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val comments: List<Comment>) : UiState
        data object Disabled : UiState
        data object Error : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadedVideoId: String? = null

    /** Loads comments for [videoId] once; repeat calls for the same id are ignored. */
    fun load(videoId: String) {
        if (videoId == loadedVideoId) return
        loadedVideoId = videoId
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = comments.forVideo(videoId)) {
                is CommentsResult.Success -> UiState.Loaded(result.comments)
                CommentsResult.Disabled -> UiState.Disabled
                is CommentsResult.Failure -> UiState.Error
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CommentsViewModel(container.youTubeComments) }
        }
    }
}
