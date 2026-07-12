package com.dewijones92.uniapp.ui.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.MediaMetadata
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VideosViewModel(private val engine: YtDlpEngine) : ViewModel() {

    /** State of the current link check; the dialog renders from this. */
    sealed interface CheckState {
        data object Idle : CheckState
        data object InProgress : CheckState
        data class Found(val metadata: MediaMetadata) : CheckState

        sealed interface Error : CheckState {
            data object InvalidUrl : Error
            data object Unsupported : Error
            data object Network : Error
            data object Extraction : Error
        }
    }

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState: StateFlow<CheckState> = _checkState

    fun check(rawUrl: String) {
        val url = HttpUrl.parse(rawUrl)
        if (url == null) {
            _checkState.value = CheckState.Error.InvalidUrl
            return
        }
        viewModelScope.launch {
            _checkState.value = CheckState.InProgress
            _checkState.value = when (val result = engine.extract(url)) {
                is ExtractionResult.Success -> CheckState.Found(result.metadata)
                is ExtractionResult.Failure.UnsupportedUrl -> CheckState.Error.Unsupported
                is ExtractionResult.Failure.Network -> CheckState.Error.Network
                is ExtractionResult.Failure.Extractor -> CheckState.Error.Extraction
            }
        }
    }

    fun reset() {
        _checkState.value = CheckState.Idle
    }

    companion object {
        fun factory(engine: YtDlpEngine): ViewModelProvider.Factory = viewModelFactory {
            initializer { VideosViewModel(engine) }
        }
    }
}
