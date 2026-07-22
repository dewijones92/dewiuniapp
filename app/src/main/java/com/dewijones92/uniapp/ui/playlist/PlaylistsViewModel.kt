package com.dewijones92.uniapp.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.innertube.playlists.Playlist
import com.dewijones92.uniapp.innertube.playlists.PlaylistsResult
import com.dewijones92.uniapp.innertube.playlists.YouTubePlaylists
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs the "your playlists" list, read live from the account. */
class PlaylistsViewModel(private val playlists: YouTubePlaylists) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val playlists: List<Playlist>) : UiState
        data object SignedOut : UiState
        data object Error : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = playlists.myPlaylists()) {
                is PlaylistsResult.Success -> UiState.Loaded(result.playlists)
                PlaylistsResult.SignedOut -> UiState.SignedOut
                is PlaylistsResult.Failure -> UiState.Error
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlaylistsViewModel(container.youTubePlaylists) }
        }
    }
}
