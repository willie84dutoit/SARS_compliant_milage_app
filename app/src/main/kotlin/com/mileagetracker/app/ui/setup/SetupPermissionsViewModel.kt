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
 * T-002.1: the granted flags reflect real OS permission state, refreshed via the single entry
 * point [SetupPermissionsViewModel.applyGrantSnapshot] — called both from an explicit
 * `ContextCompat.checkSelfPermission` read on initial composition (so a *returning* user who
 * already granted everything via Settings sees accurate state instead of stale `false` defaults)
 * and from the `ActivityResultContracts` launcher callbacks' results, for the live request flow.
 * [isLimitedModeBannerVisible] follows brief §8's locked rule: limited mode is required when
 * background location OR notifications are not granted (see
 * [SetupPermissionsPlanner.isLimitedModeRequired] for the exact, tested condition — camera denial
 * alone does not trigger it).
 */
data class SetupPermissionsUiState(
    val isFineLocationGranted: Boolean = false,
    val isCoarseLocationGranted: Boolean = false,
    val isBackgroundLocationGranted: Boolean = false,
    val isCameraGranted: Boolean = false,
    val isActivityRecognitionGranted: Boolean = false,
    val isNotificationsGranted: Boolean = false,
    val isLimitedModeBannerVisible: Boolean = false,
)

@HiltViewModel
class SetupPermissionsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val planner = SetupPermissionsPlanner()

    private val _uiState = MutableStateFlow(SetupPermissionsUiState())
    val uiState: StateFlow<SetupPermissionsUiState> = _uiState.asStateFlow()

    /**
     * Replaces every granted flag with the supplied snapshot (already read from
     * `ContextCompat.checkSelfPermission` or an `ActivityResult` map by the caller — this
     * ViewModel has no `Context`, by design, per the architecture boundary that UI-framework
     * objects never reach into the domain/ViewModel layer) and recomputes
     * [SetupPermissionsUiState.isLimitedModeBannerVisible] from it.
     */
    fun applyGrantSnapshot(snapshot: PermissionGrantSnapshot) {
        _uiState.value = _uiState.value.copy(
            isFineLocationGranted = snapshot.isFineLocationGranted,
            isCoarseLocationGranted = snapshot.isCoarseLocationGranted,
            isBackgroundLocationGranted = snapshot.isBackgroundLocationGranted,
            isCameraGranted = snapshot.isCameraGranted,
            isActivityRecognitionGranted = snapshot.isActivityRecognitionGranted,
            isNotificationsGranted = snapshot.isNotificationsGranted,
            isLimitedModeBannerVisible = planner.isLimitedModeRequired(snapshot),
        )
    }

    fun onSetupComplete() {
        viewModelScope.launch {
            settingsRepository.setHasCompletedFirstRunSetup(true)
        }
    }
}
