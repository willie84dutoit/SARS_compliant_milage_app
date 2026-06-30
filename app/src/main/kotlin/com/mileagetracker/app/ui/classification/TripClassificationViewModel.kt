package com.mileagetracker.app.ui.classification

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.data.signing.TripSigningOrchestrator
import com.mileagetracker.app.domain.classification.ClassificationRules
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.ocr.OdometerOcrClient
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import com.mileagetracker.app.domain.repository.TripPhotoRepository
import com.mileagetracker.app.domain.repository.TripRepository
import com.mileagetracker.app.domain.repository.TripWriteResult
import com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine
import com.mileagetracker.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val CLASSIFICATION_UI_LOG_TAG = "MT-UI"
private const val CLASSIFICATION_TRIP_LOG_TAG = "MT-Trip"
private const val CLASSIFICATION_OCR_LOG_TAG = "MT-OCR"

/**
 * Unified state for the single-screen trip-completion flow (Classification + Odometer).
 *
 * Odometer fields were formerly in OdometerCaptureViewModel; they move here as part of the
 * screen consolidation: one screen, one Save, for classification + odometer photo + reading.
 *
 * [photoRetentionMode] is loaded from the trip entity in init. Conservative default SAVED means a
 * race where the trip loads after the user takes a photo retains the photo rather than deleting it.
 *
 * [capturedBitmap] is held only for thumbnail display — not serialised to SavedStateHandle
 * (Bitmap is not parcelable). On process death the user retakes the photo.
 *
 * [isCameraPreviewVisible] drives the inline camera overlay composable on/off.
 *
 * [ocrResult] is the raw sealed result from OdometerOcrClient used only to display context labels.
 * [odometerReadingText] is the editable source-of-truth for what actually gets persisted.
 *
 * [odometerReadingValidationError] is set when the reading field is non-empty but invalid.
 * It is NOT set when the field is empty (empty → skip odometer write, brief §5.3).
 *
 * [saveError] is set when the save coroutine throws — never swallowed.
 */
data class TripClassificationUiState(
    val trip: Trip? = null,
    val selectedClassification: TripClassification? = null,
    val businessReasonText: String = "",
    val validationErrorMessage: String? = null,
    val isSaving: Boolean = false,

    // --- Odometer section (moved from OdometerCaptureViewModel) ---
    val photoRetentionMode: PhotoRetentionMode = PhotoRetentionMode.SAVED,
    val capturedBitmap: Bitmap? = null,
    val capturedImageUri: String? = null,
    val isOcrInProgress: Boolean = false,
    val ocrResult: OdometerOcrResult? = null,
    val isCameraPreviewVisible: Boolean = false,

    /**
     * Editable odometer reading field. Pre-filled from OCR (all three arms — fix for bug where
     * Confident arm previously left the field blank):
     *   Confident     → valueKm.toString()
     *   LowConfidence → bestGuessValueKm?.toString() ?: ""
     *   NoTextFound   → ""
     * The user may correct any of these before saving.
     * Field is unconditionally visible regardless of camera/photo state (geo-sensors refinement #6).
     */
    val odometerReadingText: String = "",
    val odometerReadingValidationError: String? = null,
    val saveError: String? = null,
)

@HiltViewModel
class TripClassificationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val tripPhotoRepository: TripPhotoRepository,
    private val odometerOcrClient: OdometerOcrClient,
    private val tripSigningOrchestrator: TripSigningOrchestrator,
    // P0.3: injected rather than constructed directly so tests can substitute a controlled
    // instance and the binding is consistent with the rest of the Hilt graph. The state machine
    // holds no mutable state (pure transition logic), so sharing a @Singleton instance between
    // this ViewModel and TripTrackingForegroundService is safe.
    private val tripLifecycleStateMachine: TripLifecycleStateMachine,
) : ViewModel() {

    private val tripId: String = requireNotNull(savedStateHandle[Screen.TripClassification.ARG_TRIP_ID])

    private val _uiState = MutableStateFlow(TripClassificationUiState())
    val uiState: StateFlow<TripClassificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val loadedTrip = tripRepository.getTripById(tripId)
            Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).i(
                "TripClassificationScreen: loaded trip tripId=%s found=%s",
                tripId,
                loadedTrip != null,
            )
            _uiState.value = _uiState.value.copy(
                trip = loadedTrip,
                selectedClassification = loadedTrip?.classification,
                businessReasonText = loadedTrip?.businessReason.orEmpty(),
                photoRetentionMode = loadedTrip?.photoRetention ?: PhotoRetentionMode.SAVED,
            )
        }
    }

    // ------------------------------------------------------------------
    // Classification section
    // ------------------------------------------------------------------

    fun onClassificationSelected(classification: TripClassification) {
        Timber.tag(CLASSIFICATION_UI_LOG_TAG).i(
            "TripClassificationScreen: classification selected tripId=%s classification=%s",
            tripId,
            classification,
        )
        _uiState.value = _uiState.value.copy(
            selectedClassification = classification,
            validationErrorMessage = null,
        )
    }

    fun onBusinessReasonChanged(newBusinessReasonText: String) {
        _uiState.value = _uiState.value.copy(
            businessReasonText = newBusinessReasonText,
            validationErrorMessage = null,
        )
    }

    // ------------------------------------------------------------------
    // Odometer / Camera section
    // ------------------------------------------------------------------

    fun onTakeOdometerPhotoClicked() {
        Timber.tag(CLASSIFICATION_UI_LOG_TAG).i(
            "TripClassificationScreen: Take odometer photo tapped tripId=%s",
            tripId,
        )
        _uiState.value = _uiState.value.copy(isCameraPreviewVisible = true)
    }

    fun onCameraOverlayDismissed() {
        Timber.tag(CLASSIFICATION_UI_LOG_TAG).i(
            "TripClassificationScreen: Camera overlay dismissed tripId=%s",
            tripId,
        )
        _uiState.value = _uiState.value.copy(isCameraPreviewVisible = false)
    }

    /**
     * Called by the camera overlay when CameraX capture fails OR when bitmap decode returns null
     * (T-031 N-3). Closes the camera overlay and surfaces the error in [saveError] so the user
     * sees visible feedback and the retake button remains accessible.
     *
     * This reuses the existing [saveError] state field (also used by [onSaveClassification]) to
     * avoid adding a new error channel — both represent "something went wrong; look at this message
     * and try again."
     */
    fun onCaptureOrDecodeError(errorMessage: String) {
        Timber.tag(CLASSIFICATION_UI_LOG_TAG).e(
            "TripClassificationScreen: capture/decode error tripId=%s error=%s",
            tripId,
            errorMessage,
        )
        _uiState.value = _uiState.value.copy(
            isCameraPreviewVisible = false,
            isOcrInProgress = false,
            saveError = "Photo capture failed — please retake",
        )
    }

    /**
     * Receives the bitmap captured by CameraX (already rotated to upright by the caller), runs
     * the ML Kit OCR pipeline, and updates state with the result.
     *
     * Pre-fill contract (ml-ocr refinement — fixes the bug where the Confident arm never wrote to
     * the editable field):
     *   Confident     → odometerReadingText = valueKm.toString()
     *   LowConfidence → odometerReadingText = bestGuessValueKm?.toString() ?: ""
     *   NoTextFound   → odometerReadingText = ""
     *
     * The reading field and OCR context label are NOT rendered while [isOcrInProgress] is true
     * to avoid empty→prefilled flicker.
     *
     * Null bitmap → OdometerOcrClient contract returns NoTextFound. No special-casing here.
     */
    fun captureAndRunOcr(capturedBitmap: Bitmap?, capturedImageUri: String) {
        Timber.tag(CLASSIFICATION_UI_LOG_TAG).i(
            "TripClassificationScreen: capture complete, running OCR tripId=%s imageUri=%s",
            tripId,
            capturedImageUri,
        )
        _uiState.value = _uiState.value.copy(
            isCameraPreviewVisible = false,
            isOcrInProgress = true,
            capturedBitmap = capturedBitmap,
            capturedImageUri = capturedImageUri,
        )
        viewModelScope.launch {
            val startOdometerKm = tripRepository.getTripById(tripId)?.startOdometerKm ?: run {
                Timber.tag(CLASSIFICATION_OCR_LOG_TAG).w(
                    "captureAndRunOcr: trip not found for tripId=%s; defaulting startOdometerKm=0.0",
                    tripId,
                )
                0.0
            }
            val ocrResult = odometerOcrClient.recognizeText(capturedBitmap, startOdometerKm)

            val resultLogLabel = when (ocrResult) {
                is OdometerOcrResult.Confident ->
                    "Confident(valueKm=${ocrResult.valueKm}, confidence=${ocrResult.confidencePercent}%)"
                is OdometerOcrResult.LowConfidence ->
                    "LowConfidence(bestGuess=${ocrResult.bestGuessValueKm}, confidence=${ocrResult.confidencePercent}%)"
                OdometerOcrResult.NoTextFound -> "NoTextFound"
            }
            Timber.tag(CLASSIFICATION_OCR_LOG_TAG).i(
                "TripClassificationScreen: OCR result for tripId=%s: %s",
                tripId,
                resultLogLabel,
            )

            // All three arms write the reading field explicitly (ml-ocr refinement #1).
            val prefillReadingText = when (ocrResult) {
                is OdometerOcrResult.Confident -> ocrResult.valueKm.toString()
                is OdometerOcrResult.LowConfidence -> ocrResult.bestGuessValueKm?.toString() ?: ""
                OdometerOcrResult.NoTextFound -> ""
            }

            _uiState.value = _uiState.value.copy(
                isOcrInProgress = false,
                ocrResult = ocrResult,
                odometerReadingText = prefillReadingText,
            )
        }
    }

    /**
     * Resets OCR / camera state so the overlay reopens on the next tap.
     * Clears ocrResult, capturedImageUri, capturedBitmap, AND the typed reading unconditionally
     * (ml-ocr refinement #2).
     */
    fun onRetakeOdometerPhoto() {
        Timber.tag(CLASSIFICATION_UI_LOG_TAG).i(
            "TripClassificationScreen: Retake odometer photo tapped tripId=%s",
            tripId,
        )
        _uiState.value = _uiState.value.copy(
            capturedBitmap = null,
            capturedImageUri = null,
            ocrResult = null,
            odometerReadingText = "",
            odometerReadingValidationError = null,
            isCameraPreviewVisible = false,
        )
    }

    fun onOdometerReadingChanged(newReadingText: String) {
        _uiState.value = _uiState.value.copy(
            odometerReadingText = newReadingText,
            odometerReadingValidationError = null,
        )
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    /**
     * Single Save for the unified classification + odometer screen.
     *
     * Validation (ml-ocr refinement #4):
     * 1. Classification must be selected.
     * 2. WORK: business reason non-blank.
     * 3. Reading non-empty → must parse as Double (else "Enter a valid number").
     * 4. Reading non-empty → must be >= 0.0 (else "Odometer reading cannot be negative").
     *
     * Save coroutine (single try/catch — risk mitigation from spec):
     * a. updateClassification
     * b. if reading non-empty and valid: updateVerifiedOdometer
     * c. if photo was taken: savePhotoIfRetentionEnabled
     * d. resolvePendingOcrForTrip() → signAndFinalizeTrip or updateStatus
     * e. onSaved()
     *
     * Empty reading → skip (b), trip saves with verifiedOdometerKm = null (brief §5.3).
     * A thrown exception sets [saveError] — never swallowed.
     *
     * Steps (a) and (b) return [TripWriteResult] — every variant is branched on explicitly via an
     * exhaustive `when` (no `else`), so a future variant fails to compile until handled here too.
     * [TripWriteResult.RejectedSignedRow] and [TripWriteResult.TripNotFound] both abort the save
     * and surface [saveError]; neither is swallowed.
     */
    fun onSaveClassification(onSaved: () -> Unit) {
        val currentState = _uiState.value
        val classification = currentState.selectedClassification ?: run {
            Timber.tag(CLASSIFICATION_UI_LOG_TAG).w(
                "TripClassificationScreen: Save with no classification selected tripId=%s",
                tripId,
            )
            return
        }

        Timber.tag(CLASSIFICATION_UI_LOG_TAG).i(
            "TripClassificationScreen: Save clicked tripId=%s classification=%s",
            tripId,
            classification,
        )

        if (classification == TripClassification.WORK) {
            val businessReasonValidation =
                ClassificationRules.validateBusinessReason(currentState.businessReasonText)
            if (businessReasonValidation is ClassificationRules.ValidationResult.Invalid) {
                Timber.tag(CLASSIFICATION_UI_LOG_TAG).e(
                    "TripClassificationScreen: Save blocked by business-reason validation tripId=%s reason=%s",
                    tripId,
                    businessReasonValidation.reason,
                )
                _uiState.value = currentState.copy(validationErrorMessage = businessReasonValidation.reason)
                return
            }
        }

        val readingText = currentState.odometerReadingText.trim()
        val verifiedOdometerKmOrNull: Double? = if (readingText.isNotEmpty()) {
            val parsedReading = readingText.toDoubleOrNull()
            if (parsedReading == null) {
                _uiState.value = currentState.copy(odometerReadingValidationError = "Enter a valid number")
                return
            }
            if (parsedReading < 0.0) {
                _uiState.value = currentState.copy(odometerReadingValidationError = "Odometer reading cannot be negative")
                return
            }
            parsedReading
        } else {
            null
        }

        val businessReasonToStore = if (classification == TripClassification.WORK) {
            currentState.businessReasonText
        } else {
            null
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)
            try {
                Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).i(
                    "TripClassificationScreen: writing classification tripId=%s classification=%s businessReason=%s",
                    tripId,
                    classification,
                    businessReasonToStore,
                )
                val classificationWriteResult = tripRepository.updateClassification(tripId, classification, businessReasonToStore)
                when (classificationWriteResult) {
                    TripWriteResult.Success -> Unit
                    TripWriteResult.RejectedSignedRow -> {
                        Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).w(
                            "TripClassificationScreen: classification write rejected — trip already signed tripId=%s",
                            tripId,
                        )
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            saveError = "This trip is finalized and can't be edited",
                        )
                        return@launch
                    }
                    TripWriteResult.TripNotFound -> {
                        Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).e(
                            "TripClassificationScreen: classification write failed — trip not found tripId=%s",
                            tripId,
                        )
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            saveError = "Save failed — please try again",
                        )
                        return@launch
                    }
                }

                if (verifiedOdometerKmOrNull != null) {
                    Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).i(
                        "TripClassificationScreen: writing verifiedOdometerKm=%s tripId=%s",
                        verifiedOdometerKmOrNull,
                        tripId,
                    )
                    val odometerWriteResult = tripRepository.updateVerifiedOdometer(tripId, verifiedOdometerKmOrNull)
                    when (odometerWriteResult) {
                        TripWriteResult.Success -> Unit
                        TripWriteResult.RejectedSignedRow -> {
                            Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).w(
                                "TripClassificationScreen: odometer write rejected — trip already signed tripId=%s",
                                tripId,
                            )
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "This trip is finalized and can't be edited",
                            )
                            return@launch
                        }
                        TripWriteResult.TripNotFound -> {
                            Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).e(
                                "TripClassificationScreen: odometer write failed — trip not found tripId=%s",
                                tripId,
                            )
                            _uiState.value = _uiState.value.copy(
                                isSaving = false,
                                saveError = "Save failed — please try again",
                            )
                            return@launch
                        }
                    }
                } else {
                    Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).i(
                        "TripClassificationScreen: no reading provided — skipping updateVerifiedOdometer tripId=%s",
                        tripId,
                    )
                }

                val capturedUri = currentState.capturedImageUri
                if (capturedUri != null) {
                    Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).i(
                        "TripClassificationScreen: handling photo retention tripId=%s retention=%s",
                        tripId,
                        currentState.photoRetentionMode,
                    )
                    tripPhotoRepository.savePhotoIfRetentionEnabled(
                        tripId = tripId,
                        imageUri = capturedUri,
                        retentionMode = currentState.photoRetentionMode,
                    )
                }

                resolvePendingOcrForTrip()

                _uiState.value = _uiState.value.copy(isSaving = false)
                onSaved()
            } catch (cancellationException: CancellationException) {
                // P0.1: rethrow CancellationException so structured concurrency can propagate
                // the cancellation correctly (e.g. when viewModelScope is cancelled on ViewModel
                // clear). Swallowing it would silently break coroutine cancellation and leave
                // isSaving=true on a destroyed ViewModel. Pattern mirrors MileageTrackerApplication
                // .launchChainTailSelfHeal() exactly.
                throw cancellationException
            } catch (saveCaughtException: Exception) {
                Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).e(
                    saveCaughtException,
                    "TripClassificationScreen: save coroutine failed tripId=%s",
                    tripId,
                )
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = "Save failed — please try again",
                )
            }
        }
    }

    /**
     * Resolves PENDING_OCR after the user confirms classification + odometer on the merged screen.
     * Moved verbatim from OdometerCaptureViewModel — logic and semantics are identical.
     *
     * COMPLETED → [TripSigningOrchestrator.signAndFinalizeTrip] (T-008, never throws).
     * Other → plain [TripRepository.updateStatus].
     */
    private suspend fun resolvePendingOcrForTrip() {
        val tripAfterWrites = tripRepository.getTripById(tripId) ?: run {
            Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).e(
                "TripClassificationScreen: resolvePendingOcrForTrip — trip not found tripId=%s",
                tripId,
            )
            return
        }
        val resolvedStatus = tripLifecycleStateMachine.resolvePendingOcrAfterOdometerConfirmed(
            classification = tripAfterWrites.classification,
            businessReason = tripAfterWrites.businessReason,
        )
        Timber.tag(CLASSIFICATION_TRIP_LOG_TAG).i(
            "TripClassificationScreen: resolving PENDING_OCR -> %s tripId=%s",
            resolvedStatus,
            tripId,
        )
        when (resolvedStatus) {
            TripStatus.COMPLETED -> {
                // T-008: COMPLETED is the signing call site. Orchestrator signs, writes to Room,
                // flips status, advances DataStore tail. Never throws — signing failure still
                // results in a completed trip with null signature fields.
                tripSigningOrchestrator.signAndFinalizeTrip(tripId)
            }
            else -> {
                tripRepository.updateStatus(tripId, resolvedStatus)
            }
        }
    }
}
