package de.crunchymate.ui.licenses

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LOG_TAG = "CrunchyMate"

sealed interface LicensesUiState {
    data object Loading : LicensesUiState

    data object Failed : LicensesUiState

    data class Loaded(
        val notices: OpenSourceNotices,
    ) : LicensesUiState
}

class LicensesViewModel(
    private val source: NoticesSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LicensesUiState>(LicensesUiState.Loading)
    val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value =
                try {
                    LicensesUiState.Loaded(source.load())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Lizenzhinweise konnten nicht geladen werden: ${e::class.simpleName}")
                    LicensesUiState.Failed
                }
        }
    }
}
