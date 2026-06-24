package com.mileagetracker.app.ui.odometer

import android.graphics.Bitmap
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
import timber.log.Timber
import javax.inject.Inject

/**
 * T-001 blueprint §5, row 4 / T-005.1: CameraX preview/capture state, OCR-in-progress flag,
 * OCR result, manual-entry fallback text, and the captured image URI used when the user confirms
 * an OCR result.
 *
 * [capturedImageUri] is populated in [captureAndRunOcr] and consumed by [onConfirmOcrResult] so
 * the photo-retention path always has the correct URI regardless of which confirm button the user
 * taps.
 */
data class OdometerCaptureUiState(
    val isCaptureInProgress: Boolean = false,
    val isOcrInProgress: Boolean = false,
    val ocrResult: OdometerOcrResult? = null,
    val manualEntryText: String = "",
    val capturedImageUri: String? = null,
    /**
     * Loaded from [Trip.photoRetention] in [OdometerCaptureViewModel.init]. Defaults to SAVED so
     * that any race where the trip loads after the user somehow confirms is safe (SAVED is the
     * conservative, non-destructive default — the photo is kept rather than deleted prematurely).
     */
    val photoRetentionMode: PhotoRetentionMode = PhotoRetentionMode.SAVED,
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

    init {
        viewModelScope.launch {
            val tripPhotoRetention = tripRepository.getTripById(tripId)?.photoRetention
            if (tripPhotoRetention != null) {
                _uiState.value = _uiState.value.copy(photoRetentionMode = tripPhotoRetention)
            }
        }
    }

    fun onManualEntryChanged(newText: String) {
        _uiState.value = _uiState.value.copy(manualEntryText = newText)
    }

    /**
     * Resets the OCR result so the camera preview is shown again (user tapped "Retake").
     * Also clears the captured image URI so a stale URI cannot be accidentally confirmed.
     */
    fun onRetakePhoto() {
        Timber.tag("MT-UI").i("OdometerCaptureScreen: Retake tapped for tripId=%s", tripId)
        _uiState.value = _uiState.value.copy(
            ocrResult = null,
            capturedImageUri = null,
            manualEntryText = "",
        )
    }

    /**
     * T-005.1: receives the bitmap captured by CameraX (already rotated to upright by the caller),
     * runs the ML Kit OCR pipeline via [odometerOcrClient], and updates [uiState] with the result.
     *
     * [photoRetentionMode] is read from [Trip.photoRetention] — the retention mode is stored on the
     * trip entity at creation time so that a later Settings change never reinterprets captured
     * photos for existing trips. The caller (OdometerCaptureScreen) reads it from the trip loaded
     * in the ViewModel rather than from a live SettingsRepository observation, keeping the
     * retention decision stable for the lifetime of this capture session.
     *
     * Null-trip fallback: if [TripRepository.getTripById] returns null (e.g. a concurrent delete
     * that should never happen in normal flow), [startOdometerKm] defaults to 0.0 so OCR still
     * runs and the user can confirm a manual value. This is a graceful-degradation path, not a
     * correctness guarantee.
     */
    fun captureAndRunOcr(
        capturedBitmap: Bitmap?,
        imageUri: String,
        photoRetentionMode: PhotoRetentionMode,
    ) {
        Timber.tag("MT-UI").i(
            "OdometerCaptureScreen: Capture button tapped tripId=%s, imageUri=%s, retention=%s",
            tripId,
            imageUri,
            photoRetentionMode,
        )
        _uiState.value = _uiState.value.copy(
            isOcrInProgress = true,
            capturedImageUri = imageUri,
        )
        viewModelScope.launch {
            val startOdometerKm = tripRepository.getTripById(tripId)?.startOdometerKm ?: run {
                Timber.tag("MT-OCR").w(
                    "captureAndRunOcr: trip not found for tripId=%s; defaulting startOdometerKm=0.0",
                    tripId,
                )
                0.0
            }
            val ocrResult = odometerOcrClient.recognizeText(capturedBitmap, startOdometerKm)

            val resultLogLabel = when (ocrResult) {
                is OdometerOcrResult.Confident -> "Confident(valueKm=${ocrResult.valueKm}, confidence=${ocrResult.confidencePercent}%)"
                is OdometerOcrResult.LowConfidence -> "LowConfidence(bestGuess=${ocrResult.bestGuessValueKm}, confidence=${ocrResult.confidencePercent}%)"
                OdometerOcrResult.NoTextFound -> "NoTextFound"
            }
            Timber.tag("MT-OCR").i(
                "OdometerCaptureScreen: OCR result for tripId=%s: %s",
                tripId,
                resultLogLabel,
            )

            val prefillText = if (ocrResult is OdometerOcrResult.LowConfidence) {
                ocrResult.bestGuessValueKm?.toString() ?: ""
            } else {
                _uiState.value.manualEntryText
            }

            _uiState.value = _uiState.value.copy(
                isOcrInProgress = false,
                ocrResult = ocrResult,
                manualEntryText = prefillText,
            )
        }
    }

    /**
     * Confirms a manual odometer value, bypassing OCR entirely. Brief §5.3/§5.9: the trip must
     * still save even when OCR is unavailable — this path exists for exactly that case. Per
     * blueprint §4 "completion is two-part," this is also the call site that resolves the trip's
     * PENDING_OCR status into COMPLETED or PENDING_BUSINESS_REASON via the state machine.
     */
    fun onConfirmManualOdometer(onConfirmed: () -> Unit) {
        val manualValueKm = _uiState.value.manualEntryText.toDoubleOrNull() ?: return
        Timber.tag("MT-UI").i("OdometerCaptureScreen: manual odometer confirmed tripId=%s, valueKm=%s", tripId, manualValueKm)
        viewModelScope.launch {
            Timber.tag("MT-Trip").i("OdometerCaptureScreen: writing verifiedOdometerKm=%s for tripId=%s (manual entry)", manualValueKm, tripId)
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
        Timber.tag("MT-UI").i("OdometerCaptureScreen: OCR result confirmed tripId=%s, valueKm=%s", tripId, valueKm)
        viewModelScope.launch {
            Timber.tag("MT-Trip").i("OdometerCaptureScreen: writing verifiedOdometerKm=%s for tripId=%s (OCR-confirmed)", valueKm, tripId)
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
        Timber.tag("MT-Trip").i("OdometerCaptureScreen: resolving PENDING_OCR -> %s for tripId=%s", resolvedStatus, tripId)
        tripRepository.updateStatus(tripId, resolvedStatus)
    }
}
