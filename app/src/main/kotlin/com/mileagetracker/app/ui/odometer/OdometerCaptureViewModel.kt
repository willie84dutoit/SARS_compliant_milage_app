package com.mileagetracker.app.ui.odometer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.ocr.OdometerOcrClient
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import com.mileagetracker.app.domain.repository.TripPhotoRepository
import com.mileagetracker.app.domain.repository.TripRepository
import com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine
import com.mileagetracker.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * T-001 blueprint §5, row 4: CameraX preview/capture state, OCR-in-progress flag, OCR result,
 * manual-entry fallback text. This is a SHELL for T-001 — the real CameraX wiring and the
 * OCR-result branching (confident/low-confidence/no-text -> exact UI state + whether the photo
 * is persisted per the retention rule) is finished in T-005 coordinating with ml-ocr-specialist,
 * per the blueprint §6 delegation split (kept with android-engineer, not handed to the coder).
 */
data class OdometerCaptureUiState(
    val isCaptureInProgress: Boolean = false,
    val isOcrInProgress: Boolean = false,
    val ocrResult: OdometerOcrResult? = null,
    val manualEntryText: String = "",
)

@HiltViewModel
class OdometerCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val tripPhotoRepository: TripPhotoRepository,
    private val odometerOcrClient: OdometerOcrClient,
) : ViewModel() {

    private val tripId: String = requireNotNull(savedStateHandle[Screen.OdometerCapture.ARG_TRIP_ID])
    private val tripLifecycleStateMachine = TripLifecycleStateMachine()

    private val _uiState = MutableStateFlow(OdometerCaptureUiState())
    val uiState: StateFlow<OdometerCaptureUiState> = _uiState.asStateFlow()

    fun onManualEntryChanged(newText: String) {
        _uiState.value = _uiState.value.copy(manualEntryText = newText)
    }

    /**
     * Confirms a manual odometer value, bypassing OCR entirely. Brief §5.3/§5.9: the trip must
     * still save even when OCR is unavailable — this path exists for exactly that case. Per
     * blueprint §4 "completion is two-part," this is also the call site that resolves the trip's
     * PENDING_OCR status into COMPLETED or PENDING_BUSINESS_REASON via the state machine.
     */
    fun onConfirmManualOdometer(onConfirmed: () -> Unit) {
        val manualValueKm = _uiState.value.manualEntryText.toDoubleOrNull() ?: return
        viewModelScope.launch {
            tripRepository.updateVerifiedOdometer(tripId, manualValueKm)
            resolvePendingOcrForTrip()
            onConfirmed()
        }
    }

    /**
     * Confirms an OCR result the user has accepted (>=80% confidence per
     * [OdometerOcrResult.Confident]). [photoRetention] gates whether the captured photo is kept
     * per [TripPhotoRepository.savePhotoIfRetentionEnabled]'s documented retention rule.
     */
    fun onConfirmOcrResult(valueKm: Double, imageUri: String, photoRetention: PhotoRetentionMode, onConfirmed: () -> Unit) {
        viewModelScope.launch {
            tripRepository.updateVerifiedOdometer(tripId, valueKm)
            tripPhotoRepository.savePhotoIfRetentionEnabled(tripId, imageUri, photoRetention)
            resolvePendingOcrForTrip()
            onConfirmed()
        }
    }

    private suspend fun resolvePendingOcrForTrip() {
        val trip = tripRepository.getTripById(tripId) ?: return
        val resolvedStatus = tripLifecycleStateMachine.resolvePendingOcrAfterOdometerConfirmed(
            classification = trip.classification,
            businessReason = trip.businessReason,
        )
        tripRepository.updateStatus(tripId, resolvedStatus)
    }
}
