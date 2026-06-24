package com.mileagetracker.app.ui.odometer

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.ocr.OdometerOcrClient
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import com.mileagetracker.app.domain.repository.FakeTripRepository
import com.mileagetracker.app.domain.repository.TripPhotoRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T-005.1 unit tests for [OdometerCaptureViewModel.captureAndRunOcr].
 *
 * CameraX integration (ProcessCameraProvider binding) cannot be tested on the plain JVM — we
 * only test the pure ViewModel logic: what happens to [OdometerCaptureUiState] after the OCR
 * client returns each [OdometerOcrResult] variant.
 *
 * Hand-written fakes only (no Mockito/MockK per project convention):
 * - [FakeTripRepository] from the shared test package
 * - [FakeOdometerOcrClient] — defined in this file, returns a preconfigured result
 * - [FakeTripPhotoRepository] — defined in this file, records calls for assertion
 *
 * Bitmap note: [android.graphics.Bitmap] is an Android platform class that throws
 * RuntimeException("Stub!") when constructed via static methods in plain JVM unit tests.
 * The ViewModel only passes the Bitmap through to the OCR client, and [FakeOdometerOcrClient]
 * ignores the actual pixel data. We pass a null reference cast as Bitmap — safe because the
 * fake never dereferences it. This is the standard Android unit-test pattern for "don't-care"
 * Android platform types.
 *
 * StateFlow emission note: the ViewModel's init block updates [photoRetentionMode] only when
 * the trip's retention mode differs from the default (SAVED). Since test trips also use SAVED,
 * the init block produces no additional StateFlow emission (StateFlow uses distinctUntilChanged).
 * Tests call [advanceUntilIdle] to drain the init block's coroutine before calling
 * [captureAndRunOcr], but do NOT await an extra item for the init block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OdometerCaptureViewModelTest {

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

    private fun buildTripWithStartOdometer(
        tripId: String,
        startOdometerKm: Double,
        photoRetention: PhotoRetentionMode = PhotoRetentionMode.SAVED,
    ) = Trip(
        id = tripId,
        classification = TripClassification.PRIVATE,
        startTimestamp = 1_000L,
        endTimestamp = 2_000L,
        startOdometerKm = startOdometerKm,
        endOdometerKm = 0.0,
        verifiedOdometerKm = null,
        distanceKm = 10.0,
        businessReason = null,
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
    )

    private fun buildViewModel(
        fakeOcrClient: FakeOdometerOcrClient,
        fakeTripRepository: FakeTripRepository = FakeTripRepository(),
    ): OdometerCaptureViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(Screen.OdometerCapture.ARG_TRIP_ID to testTripId),
        )
        return OdometerCaptureViewModel(
            savedStateHandle = savedStateHandle,
            tripRepository = fakeTripRepository,
            tripPhotoRepository = FakeTripPhotoRepository(),
            odometerOcrClient = fakeOcrClient,
        )
    }

    /**
     * Returns null typed as [Bitmap?]. The ViewModel's [OdometerCaptureViewModel.captureAndRunOcr]
     * accepts [Bitmap?] and the [FakeOdometerOcrClient] never dereferences the bitmap, so this is
     * safe. This avoids the Stub! RuntimeException that android.graphics.Bitmap constructors throw
     * when called from plain JVM unit tests.
     */
    private fun stubBitmapForTesting(): Bitmap? = null

    // ------------------------------------------------------------------
    // captureAndRunOcr — Confident result
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr with Confident result sets ocrResult to Confident and isOcrInProgress to false`() = runTest {
        val confidentOcrResult = OdometerOcrResult.Confident(
            valueKm = 125_000.0,
            confidencePercent = 95,
        )
        val fakeOcrClient = FakeOdometerOcrClient(resultToReturn = confidentOcrResult)
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildTripWithStartOdometer(testTripId, 120_000.0))

        val viewModel = buildViewModel(fakeOcrClient, fakeTripRepository)
        advanceUntilIdle() // drain ViewModel init block

        viewModel.uiState.test {
            awaitItem() // current state snapshot on subscription

            viewModel.captureAndRunOcr(
                capturedBitmap = stubBitmapForTesting(),
                imageUri = "file:///ocr_temp/odometer_123.jpg",
                photoRetentionMode = PhotoRetentionMode.SAVED,
            )

            val progressState = awaitItem()
            assertTrue(
                "isOcrInProgress should be true immediately after captureAndRunOcr is called",
                progressState.isOcrInProgress,
            )

            advanceUntilIdle()

            val resultState = awaitItem()
            assertFalse(
                "isOcrInProgress should be false after OCR client returns",
                resultState.isOcrInProgress,
            )
            assertTrue(
                "ocrResult should be Confident",
                resultState.ocrResult is OdometerOcrResult.Confident,
            )
            val confident = resultState.ocrResult as OdometerOcrResult.Confident
            assertEquals(125_000.0, confident.valueKm, 0.001)
            assertEquals(95, confident.confidencePercent)
        }
    }

    // ------------------------------------------------------------------
    // captureAndRunOcr — LowConfidence result
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr with LowConfidence result sets ocrResult and pre-fills manualEntryText with bestGuessValueKm`() = runTest {
        val lowConfidenceOcrResult = OdometerOcrResult.LowConfidence(
            bestGuessValueKm = 98_765.0,
            confidencePercent = 62,
        )
        val fakeOcrClient = FakeOdometerOcrClient(resultToReturn = lowConfidenceOcrResult)
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildTripWithStartOdometer(testTripId, 90_000.0))

        val viewModel = buildViewModel(fakeOcrClient, fakeTripRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // current state

            viewModel.captureAndRunOcr(
                capturedBitmap = stubBitmapForTesting(),
                imageUri = "file:///ocr_temp/odometer_456.jpg",
                photoRetentionMode = PhotoRetentionMode.SAVED,
            )

            awaitItem() // isOcrInProgress = true
            advanceUntilIdle()

            val resultState = awaitItem()
            assertFalse("isOcrInProgress should be false after completion", resultState.isOcrInProgress)
            assertTrue(
                "ocrResult should be LowConfidence",
                resultState.ocrResult is OdometerOcrResult.LowConfidence,
            )
            assertEquals(
                "manualEntryText should be pre-filled with bestGuessValueKm",
                "98765.0",
                resultState.manualEntryText,
            )
        }
    }

    @Test
    fun `captureAndRunOcr with LowConfidence result where bestGuessValueKm is null leaves manualEntryText empty`() = runTest {
        val lowConfidenceNoBestGuess = OdometerOcrResult.LowConfidence(
            bestGuessValueKm = null,
            confidencePercent = 45,
        )
        val fakeOcrClient = FakeOdometerOcrClient(resultToReturn = lowConfidenceNoBestGuess)
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildTripWithStartOdometer(testTripId, 50_000.0))

        val viewModel = buildViewModel(fakeOcrClient, fakeTripRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()

            viewModel.captureAndRunOcr(
                capturedBitmap = stubBitmapForTesting(),
                imageUri = "file:///ocr_temp/odometer_789.jpg",
                photoRetentionMode = PhotoRetentionMode.SAVED,
            )

            awaitItem() // isOcrInProgress = true
            advanceUntilIdle()

            val resultState = awaitItem()
            assertEquals(
                "manualEntryText should be empty when bestGuessValueKm is null",
                "",
                resultState.manualEntryText,
            )
        }
    }

    // ------------------------------------------------------------------
    // captureAndRunOcr — NoTextFound result
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr with NoTextFound result sets ocrResult to NoTextFound and isOcrInProgress to false`() = runTest {
        val fakeOcrClient = FakeOdometerOcrClient(resultToReturn = OdometerOcrResult.NoTextFound)
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildTripWithStartOdometer(testTripId, 75_000.0))

        val viewModel = buildViewModel(fakeOcrClient, fakeTripRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()

            viewModel.captureAndRunOcr(
                capturedBitmap = stubBitmapForTesting(),
                imageUri = "file:///ocr_temp/odometer_000.jpg",
                photoRetentionMode = PhotoRetentionMode.TEMPORARY,
            )

            awaitItem() // isOcrInProgress = true
            advanceUntilIdle()

            val resultState = awaitItem()
            assertFalse("isOcrInProgress should be false", resultState.isOcrInProgress)
            assertEquals(
                "ocrResult should be NoTextFound",
                OdometerOcrResult.NoTextFound,
                resultState.ocrResult,
            )
        }
    }

    // ------------------------------------------------------------------
    // captureAndRunOcr — null trip (graceful fallback)
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr with null trip gracefully falls back to startOdometerKm of 0 and still calls OCR client`() = runTest {
        val confidentOcrResult = OdometerOcrResult.Confident(
            valueKm = 50_000.0,
            confidencePercent = 88,
        )
        val fakeOcrClient = FakeOdometerOcrClient(resultToReturn = confidentOcrResult)
        // Do NOT add any trip — getTripById returns null
        val emptyFakeTripRepository = FakeTripRepository()

        val viewModel = buildViewModel(fakeOcrClient, emptyFakeTripRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()

            viewModel.captureAndRunOcr(
                capturedBitmap = stubBitmapForTesting(),
                imageUri = "file:///ocr_temp/odometer_null.jpg",
                photoRetentionMode = PhotoRetentionMode.SAVED,
            )

            awaitItem() // isOcrInProgress = true
            advanceUntilIdle()

            val resultState = awaitItem()
            assertFalse("isOcrInProgress should be false after graceful-fallback run", resultState.isOcrInProgress)
            assertTrue(
                "OCR client should have been called and returned Confident even when trip was null",
                resultState.ocrResult is OdometerOcrResult.Confident,
            )
            assertEquals(
                "startOdometerKm passed to OCR client should have been 0.0 (graceful fallback)",
                0.0,
                fakeOcrClient.lastStartOdometerKmReceived,
                0.001,
            )
        }
    }

    // ------------------------------------------------------------------
    // capturedImageUri is stored in uiState
    // ------------------------------------------------------------------

    @Test
    fun `captureAndRunOcr stores the imageUri in uiState so onConfirmOcrResult can use it`() = runTest {
        val expectedUri = "file:///ocr_temp/odometer_uri_test.jpg"
        val fakeOcrClient = FakeOdometerOcrClient(
            resultToReturn = OdometerOcrResult.Confident(valueKm = 10_000.0, confidencePercent = 90),
        )
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildTripWithStartOdometer(testTripId, 5_000.0))

        val viewModel = buildViewModel(fakeOcrClient, fakeTripRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()

            viewModel.captureAndRunOcr(
                capturedBitmap = stubBitmapForTesting(),
                imageUri = expectedUri,
                photoRetentionMode = PhotoRetentionMode.SAVED,
            )

            val progressState = awaitItem()
            assertEquals(
                "capturedImageUri should be set immediately when isOcrInProgress becomes true",
                expectedUri,
                progressState.capturedImageUri,
            )

            advanceUntilIdle()
            val finalResultState = awaitItem()

            assertEquals(
                "capturedImageUri should still be set after OCR completes",
                expectedUri,
                finalResultState.capturedImageUri,
            )
        }
    }

    // ------------------------------------------------------------------
    // onRetakePhoto resets state
    // ------------------------------------------------------------------

    @Test
    fun `onRetakePhoto resets ocrResult and capturedImageUri to null`() = runTest {
        val fakeOcrClient = FakeOdometerOcrClient(
            resultToReturn = OdometerOcrResult.Confident(valueKm = 10_000.0, confidencePercent = 85),
        )
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildTripWithStartOdometer(testTripId, 8_000.0))

        val viewModel = buildViewModel(fakeOcrClient, fakeTripRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()

            viewModel.captureAndRunOcr(
                capturedBitmap = stubBitmapForTesting(),
                imageUri = "file:///some/path.jpg",
                photoRetentionMode = PhotoRetentionMode.SAVED,
            )
            awaitItem() // isOcrInProgress = true
            advanceUntilIdle()
            awaitItem() // Confident result

            viewModel.onRetakePhoto()

            val retakeState = awaitItem()
            assertNull("ocrResult should be null after Retake", retakeState.ocrResult)
            assertNull("capturedImageUri should be null after Retake", retakeState.capturedImageUri)
            assertEquals("manualEntryText should be cleared after Retake", "", retakeState.manualEntryText)
        }
    }
}

// ------------------------------------------------------------------
// Fakes
// ------------------------------------------------------------------

/**
 * Hand-written fake [OdometerOcrClient]. Returns [resultToReturn] for every call. Also records
 * the [startOdometerKm] it received most recently so tests can assert the correct value was
 * passed in the null-trip fallback case.
 */
private class FakeOdometerOcrClient(
    private val resultToReturn: OdometerOcrResult,
) : OdometerOcrClient {

    var lastStartOdometerKmReceived: Double = -1.0
        private set

    override suspend fun recognizeText(
        odometerPhoto: Bitmap?,
        startOdometerKm: Double,
    ): OdometerOcrResult {
        lastStartOdometerKmReceived = startOdometerKm
        return resultToReturn
    }
}

/**
 * Hand-written fake [TripPhotoRepository]. Records calls for post-condition checks if needed by
 * future tests; current tests only need [savePhotoIfRetentionEnabled] to be a no-op so the
 * ViewModel coroutines complete without a real Room database.
 */
private class FakeTripPhotoRepository : TripPhotoRepository {

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
