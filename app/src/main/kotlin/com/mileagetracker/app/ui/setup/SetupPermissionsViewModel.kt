package com.mileagetracker.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the first-run Setup/Permissions screen (T-001 blueprint §5, row 1). Per the hard
 * rule, this screen never touches [com.mileagetracker.app.domain.repository.TripRepository] —
 * only [SettingsRepository], to persist the "first-run setup completed" flag.
 *
 * T-001 scaffolding: permission-grant tracking itself (which of ACCESS_FINE_LOCATION /
 * ACCESS_BACKGROUND_LOCATION / CAMERA / POST_NOTIFICATIONS are granted) is wired alongside the
 * runtime permission-request flow in a later task — this ViewModel exposes the shape only.
 */
data class SetupPermissionsUiState(
    val isFineLocationGranted: Boolean = false,
    val isBackgroundLocationGranted: Boolean = false,
    val isCameraGranted: Boolean = false,
    val isNotificationsGranted: Boolean = false,
    val isLimitedModeBannerVisible: Boolean = false,
)

@HiltViewModel
class SetupPermissionsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupPermissionsUiState())
    val uiState: StateFlow<SetupPermissionsUiState> = _uiState.asStateFlow()

    fun onSetupComplete() {
        viewModelScope.launch {
            settingsRepository.setHasCompletedFirstRunSetup(true)
        }
    }
}
