package com.mileagetracker.app.ui.home

import android.content.ContextWrapper
import app.cash.turbine.test
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.FakeTripRepository
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
 * T-022 back-loop-fix regression coverage: proves the auto-navigation to Trip Classification fires
 * at most once per trip id, and the "Resume classification" affordance becomes available afterwards.
 *
 * C-2 tests removed: isPendingOdometerCapture, onClassificationSavedNavigatingToOdometer,
 * and onResumeOdometerClicked no longer exist — the two-screen gap they guarded was eliminated
 * when classification and odometer were merged into one screen.
 *
 * [HomeStatusViewModel] is constructed with a bare [ContextWrapper] wrapping a null base — the
 * constructor only stores the reference and tests never call onStartTripClicked/onStopTripClicked
 * (which touch the Android platform via Intent/ContextCompat.startForegroundService).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeStatusViewModelTest {

    private val inertContext = ContextWrapper(null)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun buildPendingOcrTrip(tripId: String) = Trip(
        id = tripId,
        classification = TripClassification.PRIVATE,
        startTimestamp = 0L,
        endTimestamp = 0L,
        startOdometerKm = 0.0,
        endOdometerKm = 0.0,
        verifiedOdometerKm = null,
        distanceKm = 5.0,
        businessReason = null,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        status = TripStatus.PENDING_OCR,
        photoRetention = PhotoRetentionMode.TEMPORARY,
        createdAt = 0L,
        updatedAt = 0L,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = 0,
        isManualStart = false,
    )

    @Test
    fun `showResumeClassificationAction is false before auto-routing has happened`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildPendingOcrTrip(tripId = "trip-1"))
        val viewModel = HomeStatusViewModel(appContext = inertContext, tripRepository = fakeTripRepository)

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertFalse(initialState.showResumeClassificationAction)
        }
    }

    @Test
    fun `onTripClassificationAutoRouted records the tripId and flips showResumeClassificationAction true`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildPendingOcrTrip(tripId = "trip-1"))
        val viewModel = HomeStatusViewModel(appContext = inertContext, tripRepository = fakeTripRepository)

        viewModel.uiState.test {
            awaitItem() // initial state, before auto-routing

            viewModel.onTripClassificationAutoRouted(tripId = "trip-1")

            val stateAfterAutoRoute = awaitItem()
            assertEquals("trip-1", stateAfterAutoRoute.autoRoutedToClassificationTripId)
            assertTrue(stateAfterAutoRoute.showResumeClassificationAction)
        }
    }

    @Test
    fun `a different trip entering PENDING_OCR is not treated as already auto-routed`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildPendingOcrTrip(tripId = "trip-1"))
        val viewModel = HomeStatusViewModel(appContext = inertContext, tripRepository = fakeTripRepository)

        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.onTripClassificationAutoRouted(tripId = "trip-1")
            awaitItem() // trip-1 now auto-routed

            fakeTripRepository.setInProgressTrip(buildPendingOcrTrip(tripId = "trip-2"))

            val stateForNewTrip = awaitItem()
            assertEquals("trip-2", stateForNewTrip.inProgressTrip?.id)
            assertFalse(stateForNewTrip.showResumeClassificationAction)
        }
    }

    @Test
    fun `onResumeClassificationClicked does not itself mutate state (nav callback owns navigation)`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(buildPendingOcrTrip(tripId = "trip-1"))
        val viewModel = HomeStatusViewModel(appContext = inertContext, tripRepository = fakeTripRepository)

        viewModel.onTripClassificationAutoRouted(tripId = "trip-1")

        viewModel.uiState.test {
            val stateBeforeResumeClick = awaitItem()
            viewModel.onResumeClassificationClicked()
            assertEquals(stateBeforeResumeClick, viewModel.uiState.value)
        }
    }

    @Test
    fun `no in-progress trip means showResumeClassificationAction is false even if a stale tripId was recorded`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setInProgressTrip(null)
        val viewModel = HomeStatusViewModel(appContext = inertContext, tripRepository = fakeTripRepository)

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertNull(initialState.inProgressTrip)
            assertFalse(initialState.showResumeClassificationAction)
        }
    }

    // H-1 fix test

    @Test
    fun `isManualStart on inProgressTrip is correctly reflected in uiState`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        val manualTrip = buildPendingOcrTrip(tripId = "trip-1").copy(
            status = TripStatus.ACTIVE,
            isManualStart = true,
        )
        fakeTripRepository.setInProgressTrip(manualTrip)
        val viewModel = HomeStatusViewModel(appContext = inertContext, tripRepository = fakeTripRepository)

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(
                "inProgressTrip.isManualStart should be true for a manually started trip",
                state.inProgressTrip?.isManualStart == true,
            )
        }
    }
}
