package com.dewijones92.uniapp.ui.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.importexport.ImportOutcome
import com.dewijones92.uniapp.importexport.ImportSummary
import com.dewijones92.uniapp.importexport.SubscriptionImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Import / Export screen: runs an import off the main thread and
 * exposes its progress/result, and builds the OPML export on demand. File IO
 * (reading the picked file, writing the export) stays in the composable, which
 * owns the `Context`; this holds no Android types.
 */
class ImportExportViewModel(
    private val importer: SubscriptionImporter,
    val signedIn: StateFlow<Boolean>,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data class Imported(val summary: ImportSummary) : State
        data class Failed(val detail: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun import(content: String) {
        _state.value = State.Working
        // Default dispatcher: parsing (DOM/JSON over a whole export file) is CPU work
        // that must not block the main thread; downstream network calls switch to IO themselves.
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = when (val outcome = importer.import(content)) {
                is ImportOutcome.Done -> State.Imported(outcome.summary)
                is ImportOutcome.ParseError -> State.Failed(outcome.detail)
            }
        }
    }

    suspend fun buildOpml(): String = importer.exportOpml()

    fun clearResult() {
        _state.value = State.Idle
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ImportExportViewModel(
                    importer = container.subscriptionImporter,
                    signedIn = container.accountSubscriptions.signedIn,
                )
            }
        }
    }
}
