package com.mileagetracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.BuildConfig
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
import timber.log.Timber
import javax.inject.Inject

/** Result of a debug log export attempt, rendered as UI state. */
sealed interface DebugLogExportUiResult {
    data object Idle : DebugLogExportUiResult
    data class Success(val filename: String) : DebugLogExportUiResult
    data class Failure(val message: String) : DebugLogExportUiResult
}

/**
 * T-001 blueprint §5, row 7: photo-retention toggle + Bluetooth vehicle-trigger toggle.
 *
 * [isDebugLogExportAvailable] is true only in DEBUG builds (T-030 P0.4 Option A). The Settings
 * screen hides the "Export debugging logs" row entirely in release builds so the entry point is
 * never reachable by end-users.
 * PII-redaction / FileProvider-share deferred to a pre-Play-Store task (T-038 / T-030 P0.4 follow-up).
 */
data class SettingsUiState(
    val photoRetentionMode: PhotoRetentionMode = PhotoRetentionMode.SAVED,
    val isBluetoothVehicleTriggerEnabled: Boolean = false,
    val lastDebugLogExportResult: DebugLogExportUiResult = DebugLogExportUiResult.Idle,
    val isDebugLogExportAvailable: Boolean = false,
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
            // T-030 P0.4 Option A: expose availability flag so the Compose layer can gate the UI
            // row without referencing BuildConfig directly (keeps BuildConfig out of the UI layer).
            isDebugLogExportAvailable = BuildConfig.DEBUG,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SettingsUiState(),
    )

    fun onPhotoRetentionToggled(isSaveEnabled: Boolean) {
        Timber.tag("MT-UI").i("SettingsScreen: photo retention toggled to isSaveEnabled=%s", isSaveEnabled)
        viewModelScope.launch {
            val mode = if (isSaveEnabled) PhotoRetentionMode.SAVED else PhotoRetentionMode.TEMPORARY
            settingsRepository.setPhotoRetentionMode(mode)
        }
    }

    fun onBluetoothVehicleTriggerToggled(isEnabled: Boolean) {
        Timber.tag("MT-UI").i("SettingsScreen: Bluetooth vehicle trigger toggled to isEnabled=%s", isEnabled)
        viewModelScope.launch {
            settingsRepository.setBluetoothVehicleTriggerEnabled(isEnabled)
        }
    }

    fun onExportDebugLogClicked() {
        // T-030 P0.4 Option A: defense-in-depth guard. The Settings screen already hides this
        // row in release builds, but guard here too in case the call site is ever reached via
        // deep-link or future refactor. No-op in release so no PII is ever copied to Downloads.
        // PII-redaction / FileProvider-share deferred to a pre-Play-Store task (T-038 / T-030 P0.4 follow-up).
        if (!BuildConfig.DEBUG) {
            Timber.tag("MT-UI").w("SettingsScreen: onExportDebugLogClicked called in release build — ignoring")
            return
        }
        Timber.tag("MT-UI").i("SettingsScreen: Export debug log button clicked")
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
