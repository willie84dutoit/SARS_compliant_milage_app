package com.mileagetracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.data.logging.DebugLogExportResult
import com.mileagetracker.app.data.logging.DebugLogFileProvider
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Result of a debug log export attempt, rendered as UI state. */
sealed interface DebugLogExportUiResult {
    data object Idle : DebugLogExportUiResult
    data class Success(val filename: String) : DebugLogExportUiResult
    data class Failure(val message: String) : DebugLogExportUiResult
}

/** T-001 blueprint §5, row 7: photo-retention toggle + Bluetooth vehicle-trigger toggle. */
data class SettingsUiState(
    val photoRetentionMode: PhotoRetentionMode = PhotoRetentionMode.SAVED,
    val isBluetoothVehicleTriggerEnabled: Boolean = false,
    val lastDebugLogExportResult: DebugLogExportUiResult = DebugLogExportUiResult.Idle,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val debugLogFileProvider: DebugLogFileProvider,
) : ViewModel() {

    private val _lastDebugLogExportResult = MutableStateFlow<DebugLogExportUiResult>(DebugLogExportUiResult.Idle)
    private val lastDebugLogExportResult: StateFlow<DebugLogExportUiResult> = _lastDebugLogExportResult.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observePhotoRetentionMode(),
        settingsRepository.observeBluetoothVehicleTriggerEnabled(),
        lastDebugLogExportResult,
    ) { photoRetentionMode, isBluetoothEnabled, debugLogExportResult ->
        SettingsUiState(
            photoRetentionMode = photoRetentionMode,
            isBluetoothVehicleTriggerEnabled = isBluetoothEnabled,
            lastDebugLogExportResult = debugLogExportResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SettingsUiState(),
    )

    fun onPhotoRetentionToggled(isSaveEnabled: Boolean) {
        viewModelScope.launch {
            val mode = if (isSaveEnabled) PhotoRetentionMode.SAVED else PhotoRetentionMode.TEMPORARY
            settingsRepository.setPhotoRetentionMode(mode)
        }
    }

    fun onBluetoothVehicleTriggerToggled(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBluetoothVehicleTriggerEnabled(isEnabled)
        }
    }

    fun onExportDebugLogClicked() {
        viewModelScope.launch {
            val exportResult = withContext(Dispatchers.IO) {
                debugLogFileProvider.exportDebugLogToDownloads()
            }
            val uiResult = when (exportResult) {
                is DebugLogExportResult.Success -> DebugLogExportUiResult.Success(exportResult.filename)
                is DebugLogExportResult.Failure -> DebugLogExportUiResult.Failure(exportResult.message)
            }
            _lastDebugLogExportResult.value = uiResult
        }
    }
}
