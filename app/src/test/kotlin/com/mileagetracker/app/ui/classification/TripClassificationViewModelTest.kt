package com.mileagetracker.app.ui.classification

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mileagetracker.app.data.signing.TripSigningOrchestrator
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.ocr.OdometerOcrClient
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import com.mileagetracker.app.domain.repository.FakeTripRepository
import com.mileagetracker.app.domain.repository.TripPhotoRepository
import com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine
import com.mileagetracker.app.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TripClassificationViewModel] covering the merged classification + odometer
 * single-screen flow.
 *
 * These tests replace the deleted OdometerCaptureViewModelTest. Coverage:
 *   - captureAndRunOcr: all three OCR arms, pre-fill behaviour (including the Confident-arm
 *     fix where valueKm must be written to the reading field).
 *   - onRetakeOdometerPhoto: clears all OCR/camera state unconditionally.
 *   - onSaveClassification: save with photo+reading, save without photo (null reading), save
 *     with invalid reading text (inline error), save with negative reading (inline error).
 *   - Classification validation: WORK + blank reason → validationErrorMessage.
 *
 * Bitmap note: android.graphics.Bitmap throws RuntimeException("Stub!") from static factories in
 * plain JVM tests. We pass a null reference cast as Bitmap? — the FakeOdometerOcrClient never
 * dereferences it. This is the standard Android unit-test "don't-care" pattern.
 *
 * StateFlow / init note: the ViewModel's init block runs getTripById in a coroutine. Tests call
 * advanceUntilIdle() to drain it before exercising other methods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripClassificationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testTripId = "test-trip-001"

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildPendingOcrTrip(
        tripId: String = testTripId,
        classification: TripClassification = TripClassification.PRIVATE,
        startOdometerKm: Double = 100_000.0,
        photoRetention: PhotoRetentionMode = PhotoRetentionMode.SAVED,
        businessReason: String? = null,
    ) = Trip(
        id = tripId,
        classification = classification,
        startTimestamp = 1_000L,
        endTimestamp = 2_000L,
        startOdometerKm = startOdometerKm,
        endOdometerKm = 0.0,
        verifiedOdometerKm = null,
        distanceKm = 10.0,
        businessReason = businessReason,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        status = TripStatus.PENDING_OCR,
        photoRetention = photoRetention,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = 0,
        isManualStart = false,
    )

    private fun buildViewModel(
        fakeOcrClient: FakeClassificationOcrClient = FakeClassificationOcrClient(OdometerOcrResult.NoTextFound),
        fakeTripRepository: FakeTripRepository = FakeTripRepository(),
        fakeTripPhotoRepository: FakeClassificationPhotoRepository = FakeClassificationPhotoRepository(),
    ): TripClassificationViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(Screen.TripClassification.ARG_TRIP_ID to testTripId),
        )
        val fakeOrchestrator = FakeClassificationSigningOrchestrator(fakeTripRepository)
        return TripClassificationViewModel(
            savedStateHandle = savedStateHandle,
            tripRepository = fakeTripRepository,
            tripPhotoRepository = fakeTripPhotoRepository,
            odometerOcrClient = fakeOcrClient,
            tripSigningOrchestrator = fakeOrchestrator,
            // P0.3: state machine is now injected; tests supply a real instance directly
            // (it is stateless pure logic, no mocking needed).
            tripLifecycleStateMachine = TripLifecycleStateMachine(),
        )
    }

    private fun nullBitmapForTesting(): Bitmap? = null

    // ------------------------------------------------------------------
    // captureAndRunOcr — Confident arm (ml-ocr fix: valueKm must prefill the field)
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr with Confident result prefills odometerReadingText with valueKm`() = runTest {
        val confidentResult = OdometerOcrResult.Confident(valueKm = 125_000.0, confidencePercent = 95)
        val fakeOcrClient = FakeClassificationOcrClient(resultToReturn = confidentResult)
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip(startOdometerKm = 120_000.0))

        val viewModel = buildViewModel(fakeOcrClient = fakeOcrClient, fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // current state

            viewModel.captureAndRunOcr(nullBitmapForTesting(), "file:///ocr/odometer_1.jpg")

            val progressState = awaitItem()
            assertTrue("isOcrInProgress should be true", progressState.isOcrInProgress)

            advanceUntilIdle()
            val resultState = awaitItem()

            assertFalse("isOcrInProgress should be false after OCR", resultState.isOcrInProgress)
            assertTrue("ocrResult should be Confident", resultState.ocrResult is OdometerOcrResult.Confident)
            // Key regression check: Confident arm must write the reading field.
            assertEquals(
                "odometerReadingText should be prefilled with Confident valueKm",
                "125000.0",
                resultState.odometerReadingText,
            )
        }
    }

    // ------------------------------------------------------------------
    // captureAndRunOcr — LowConfidence arm
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr with LowConfidence result prefills field with bestGuessValueKm`() = runTest {
        val lowConfidenceResult = OdometerOcrResult.LowConfidence(bestGuessValueKm = 98_765.0, confidencePercent = 62)
        val fakeOcrClient = FakeClassificationOcrClient(resultToReturn = lowConfidenceResult)
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip(startOdometerKm = 90_000.0))

        val viewModel = buildViewModel(fakeOcrClient = fakeOcrClient, fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.captureAndRunOcr(nullBitmapForTesting(), "file:///ocr/odometer_2.jpg")
            awaitItem() // isOcrInProgress = true
            advanceUntilIdle()
            val resultState = awaitItem()

            assertEquals("98765.0", resultState.odometerReadingText)
            assertTrue(resultState.ocrResult is OdometerOcrResult.LowConfidence)
        }
    }

    @Test
    fun `captureAndRunOcr with LowConfidence null bestGuess leaves field empty`() = runTest {
        val lowConfidenceNoBestGuess = OdometerOcrResult.LowConfidence(bestGuessValueKm = null, confidencePercent = 40)
        val fakeOcrClient = FakeClassificationOcrClient(resultToReturn = lowConfidenceNoBestGuess)
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip())

        val viewModel = buildViewModel(fakeOcrClient = fakeOcrClient, fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.captureAndRunOcr(nullBitmapForTesting(), "file:///ocr/odometer_3.jpg")
            awaitItem()
            advanceUntilIdle()
            val resultState = awaitItem()

            assertEquals("", resultState.odometerReadingText)
        }
    }

    // ------------------------------------------------------------------
    // captureAndRunOcr — NoTextFound arm
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr with NoTextFound leaves reading field empty`() = runTest {
        val fakeOcrClient = FakeClassificationOcrClient(resultToReturn = OdometerOcrResult.NoTextFound)
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip())

        val viewModel = buildViewModel(fakeOcrClient = fakeOcrClient, fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.captureAndRunOcr(nullBitmapForTesting(), "file:///ocr/odometer_4.jpg")
            awaitItem()
            advanceUntilIdle()
            val resultState = awaitItem()

            assertEquals(OdometerOcrResult.NoTextFound, resultState.ocrResult)
            assertEquals("", resultState.odometerReadingText)
            assertFalse(resultState.isOcrInProgress)
        }
    }

    // ------------------------------------------------------------------
    // captureAndRunOcr — imageUri stored in state
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr stores capturedImageUri in state immediately`() = runTest {
        val expectedUri = "file:///ocr/odometer_uri_test.jpg"
        val fakeOcrClient = FakeClassificationOcrClient(OdometerOcrResult.Confident(10_000.0, 90))
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip(startOdometerKm = 5_000.0))

        val viewModel = buildViewModel(fakeOcrClient = fakeOcrClient, fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.captureAndRunOcr(nullBitmapForTesting(), expectedUri)

            val progressState = awaitItem()
            assertEquals(expectedUri, progressState.capturedImageUri)

            advanceUntilIdle()
            val finalState = awaitItem()
            assertEquals(expectedUri, finalState.capturedImageUri)
        }
    }

    // ------------------------------------------------------------------
    // onRetakeOdometerPhoto — clears all OCR/camera state
    // ------------------------------------------------------------------

    @Test
    fun `onRetakeOdometerPhoto clears ocrResult, capturedImageUri, and odometerReadingText`() = runTest {
        val fakeOcrClient = FakeClassificationOcrClient(OdometerOcrResult.Confident(10_000.0, 85))
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip(startOdometerKm = 8_000.0))

        val viewModel = buildViewModel(fakeOcrClient = fakeOcrClient, fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.captureAndRunOcr(nullBitmapForTesting(), "file:///some/path.jpg")
            awaitItem() // isOcrInProgress = true
            advanceUntilIdle()
            awaitItem() // Confident result + prefilled field

            viewModel.onRetakeOdometerPhoto()

            val retakeState = awaitItem()
            assertNull("ocrResult should be null after Retake", retakeState.ocrResult)
            assertNull("capturedImageUri should be null after Retake", retakeState.capturedImageUri)
            assertNull("capturedBitmap should be null after Retake", retakeState.capturedBitmap)
            assertEquals("odometerReadingText should be cleared after Retake", "", retakeState.odometerReadingText)
        }
    }

    // ------------------------------------------------------------------
    // onSaveClassification — validation: invalid reading text
    // ------------------------------------------------------------------

    @Test
    fun `onSaveClassification with non-numeric reading text sets odometerReadingValidationError`() = runTest {
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip())
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.onClassificationSelected(TripClassification.PRIVATE)
        viewModel.onOdometerReadingChanged("not-a-number")

        var savedCallbackFired = false
        viewModel.onSaveClassification { savedCallbackFired = true }
        advanceUntilIdle()

        assertFalse("onSaved callback must not fire when reading is invalid", savedCallbackFired)
        assertEquals(
            "Enter a valid number",
            viewModel.uiState.value.odometerReadingValidationError,
        )
    }

    @Test
    fun `onSaveClassification with negative reading sets odometerReadingValidationError`() = runTest {
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip())
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.onClassificationSelected(TripClassification.PRIVATE)
        viewModel.onOdometerReadingChanged("-100.0")

        var savedCallbackFired = false
        viewModel.onSaveClassification { savedCallbackFired = true }
        advanceUntilIdle()

        assertFalse("onSaved callback must not fire when reading is negative", savedCallbackFired)
        assertEquals(
            "Odometer reading cannot be negative",
            viewModel.uiState.value.odometerReadingValidationError,
        )
    }

    // ------------------------------------------------------------------
    // onSaveClassification — empty reading skips updateVerifiedOdometer
    // ------------------------------------------------------------------

    @Test
    fun `onSaveClassification with empty reading still completes the trip with null verifiedOdometerKm`() = runTest {
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip())
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.onClassificationSelected(TripClassification.PRIVATE)
        // Leave odometerReadingText empty (default).

        var savedCallbackFired = false
        viewModel.onSaveClassification { savedCallbackFired = true }
        advanceUntilIdle()

        assertTrue("onSaved callback must fire for empty reading", savedCallbackFired)
        // The orchestrator's fake marks the trip COMPLETED (moves it to history).
        // Verified odometer was never written so it stays null.
        val completedTrips = fakeRepo.getCompletedTripsForExport()
        assertFalse("Trip should appear in completed history", completedTrips.isEmpty())
        assertNull(
            "verifiedOdometerKm should remain null when reading field was empty",
            completedTrips.first().verifiedOdometerKm,
        )
    }

    // ------------------------------------------------------------------
    // onSaveClassification — WORK trip missing business reason
    // ------------------------------------------------------------------

    @Test
    fun `onSaveClassification WORK trip with blank reason sets validationErrorMessage`() = runTest {
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip(classification = TripClassification.WORK))
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.onClassificationSelected(TripClassification.WORK)
        viewModel.onBusinessReasonChanged("   ") // whitespace-only

        var savedCallbackFired = false
        viewModel.onSaveClassification { savedCallbackFired = true }
        advanceUntilIdle()

        assertFalse("onSaved must not fire when business reason is blank", savedCallbackFired)
        assertNotNull(viewModel.uiState.value.validationErrorMessage)
    }

    // ------------------------------------------------------------------
    // onSaveClassification — happy path with reading and photo
    // ------------------------------------------------------------------

    @Test
    fun `onSaveClassification with valid reading and photo calls photo retention and completes trip`() = runTest {
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip(startOdometerKm = 90_000.0))
        val fakePhotoRepo = FakeClassificationPhotoRepository()
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo, fakeTripPhotoRepository = fakePhotoRepo)
        advanceUntilIdle()

        // Simulate: photo was taken and OCR returned a confident result.
        val testImageUri = "file:///ocr/odometer_happy.jpg"
        viewModel.captureAndRunOcr(nullBitmapForTesting(), testImageUri)
        advanceUntilIdle()
        advanceUntilIdle() // drain OCR coroutine

        viewModel.onClassificationSelected(TripClassification.PRIVATE)
        viewModel.onOdometerReadingChanged("100000.0")

        var savedCallbackFired = false
        viewModel.onSaveClassification { savedCallbackFired = true }
        advanceUntilIdle()

        assertTrue("onSaved callback must fire on happy path", savedCallbackFired)
        assertTrue("photo repo must have been called", fakePhotoRepo.saveCallsRecorded.isNotEmpty())
        assertEquals(testImageUri, fakePhotoRepo.saveCallsRecorded.first().imageUri)
    }

    // ------------------------------------------------------------------
    // onSaveClassification — TripWriteResult.RejectedSignedRow (T-039 item 4)
    // ------------------------------------------------------------------

    @Test
    fun `onSaveClassification when trip is already signed sets finalized saveError and does not call onSaved`() = runTest {
        // A signed trip's classification write is guarded at the repository layer (T-033) and
        // returns TripWriteResult.RejectedSignedRow — the ViewModel must surface this distinctly
        // from TripNotFound rather than silently proceeding as if the save succeeded.
        val signedTrip = buildPendingOcrTrip().copy(
            status = TripStatus.COMPLETED,
            signatureBase64 = "dGVzdC1zaWduYXR1cmU=",
            signingKeyId = "mileage_tracker_signing_key_v1",
            tripSequenceNumber = 1,
        )
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(signedTrip)
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.onClassificationSelected(TripClassification.PRIVATE)

        var savedCallbackFired = false
        viewModel.onSaveClassification { savedCallbackFired = true }
        advanceUntilIdle()

        assertFalse("onSaved callback must not fire when the trip is already signed", savedCallbackFired)
        assertFalse("isSaving must be reset to false on RejectedSignedRow", viewModel.uiState.value.isSaving)
        assertEquals(
            "This trip is finalized and can't be edited",
            viewModel.uiState.value.saveError,
        )
    }

    // ------------------------------------------------------------------
    // onSaveClassification — TripWriteResult.TripNotFound (T-039 item 4)
    // ------------------------------------------------------------------

    @Test
    fun `onSaveClassification when trip not found in repository sets saveError and does not call onSaved`() = runTest {
        // Repository intentionally left empty — getTripById(testTripId) returns null both during
        // init and inside updateClassification, exercising the TripWriteResult.TripNotFound branch.
        val fakeRepo = FakeTripRepository()
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        viewModel.onClassificationSelected(TripClassification.PRIVATE)

        var savedCallbackFired = false
        viewModel.onSaveClassification { savedCallbackFired = true }
        advanceUntilIdle()

        assertFalse("onSaved callback must not fire when the trip is not found", savedCallbackFired)
        assertFalse("isSaving must be reset to false on TripNotFound", viewModel.uiState.value.isSaving)
        assertEquals(
            "Save failed — please try again",
            viewModel.uiState.value.saveError,
        )
    }

    // ------------------------------------------------------------------
    // onCaptureOrDecodeError — T-031 N-3 (visible feedback on decode failure)
    // ------------------------------------------------------------------

    @Test
    fun `onCaptureOrDecodeError closes camera overlay and sets saveError message`() = runTest {
        val fakeRepo = FakeTripRepository()
        fakeRepo.setInProgressTrip(buildPendingOcrTrip())
        val viewModel = buildViewModel(fakeTripRepository = fakeRepo)
        advanceUntilIdle()

        // Open the camera overlay first so we can verify it closes on error.
        viewModel.onTakeOdometerPhotoClicked()
        assertTrue(
            "isCameraPreviewVisible should be true after take-photo tap",
            viewModel.uiState.value.isCameraPreviewVisible,
        )

        viewModel.onCaptureOrDecodeError("Bitmap decode returned null for /ocr/odometer_test.jpg")

        val errorState = viewModel.uiState.value
        assertFalse(
            "isCameraPreviewVisible should be false after capture error",
            errorState.isCameraPreviewVisible,
        )
        assertFalse(
            "isOcrInProgress should be false after capture error",
            errorState.isOcrInProgress,
        )
        assertNotNull(
            "saveError should be non-null so the user sees visible feedback",
            errorState.saveError,
        )
    }
}

// ------------------------------------------------------------------
// Fakes — local to this test file
// ------------------------------------------------------------------

/**
 * Hand-written fake [OdometerOcrClient] for classification-screen tests. Returns
 * [resultToReturn] for every call. Also records the [startOdometerKm] it received.
 */
private class FakeClassificationOcrClient(
    private val resultToReturn: OdometerOcrResult,
) : OdometerOcrClient {

    var lastStartOdometerKmReceived: Double = -1.0
        private set

    override suspend fun recognizeText(odometerPhoto: Bitmap?, startOdometerKm: Double): OdometerOcrResult {
        lastStartOdometerKmReceived = startOdometerKm
        return resultToReturn
    }
}

/**
 * Hand-written fake [TripPhotoRepository]. Records calls so tests can assert photo retention
 * behaviour without a real Room database.
 */
private class FakeClassificationPhotoRepository : TripPhotoRepository {

    data class SaveCall(val tripId: String, val imageUri: String, val retentionMode: PhotoRetentionMode)

    val saveCallsRecorded = mutableListOf<SaveCall>()

    override suspend fun savePhotoIfRetentionEnabled(
        tripId: String,
        imageUri: String,
        retentionMode: PhotoRetentionMode,
    ) {
        saveCallsRecorded.add(SaveCall(tripId, imageUri, retentionMode))
    }

    override suspend fun deletePhotosForTrip(tripId: String) {
        // No-op for unit tests.
    }
}

/**
 * Fake [TripSigningOrchestrator] for ViewModel tests, implementing the interface directly
 * (consistent with this project's "fakes over mocks" convention — see [FakeTripRepository]).
 * [signAndFinalizeTrip] calls [FakeTripRepository.markTripCompleted] with stub signing fields —
 * simulating the "signed successfully" path without touching the Android Keystore (unavailable in
 * plain JVM tests).
 */
private class FakeClassificationSigningOrchestrator(
    private val fakeTripRepository: FakeTripRepository,
) : TripSigningOrchestrator {

    override suspend fun signAndFinalizeTrip(tripId: String) {
        fakeTripRepository.markTripCompleted(
            tripId = tripId,
            signatureBase64 = "stub-signature",
            signingKeyId = "stub-key-id",
        )
    }

    override suspend fun rebuildChainTailFromRoom() {
        // Not exercised by ViewModel tests — no-op.
    }
}
