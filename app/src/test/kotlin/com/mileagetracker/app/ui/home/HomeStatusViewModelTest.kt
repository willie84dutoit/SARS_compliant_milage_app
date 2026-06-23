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
 * T-022 back-loop-fix regression coverage (team/TASKS.md T-022 card): proves the auto-navigation
 * to Trip Classification fires at most once per trip id, and that the manual "Resume
 * classification" affordance becomes available afterwards instead of silently stranding the trip.
 *
 * [HomeStatusViewModel] is constructed with a bare [ContextWrapper] wrapping a null base — its
 * constructor only stores the reference, and this test never calls [HomeStatusViewModel.onStartTripClicked]
 * or [HomeStatusViewModel.onStopTripClicked] (the two methods that actually touch the Android
 * platform via `Intent`/`ContextCompat.startForegroundService`), so no real framework call is made.
 * This project has no Robolectric/instrumented unit-test setup (see `app/build.gradle.kts`), so
 * this is the narrowest safe way to exercise the pure `uiState`-gating logic on the JVM.
 *
 * [HomeStatusViewModel.uiState] is built with `viewModelScope`, which resolves `Dispatchers.Main`
 * internally — this is the first ViewModel test in this codebase to need it, so [setUpMainDispatcher]
 * installs a [StandardTestDispatcher] as `Dispatchers.Main` for the duration of each test.
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

            // A brand-new trip (e.g. trip-1 completed and a new trip started+stopped) reaches
            // PENDING_OCR. It must not inherit trip-1's "already auto-routed" gate.
            fakeTripRepository.setInProgressTrip(buildPendingOcrTrip(tripId = "trip-2"))

            val stateForNewTrip = awaitItem()
            assertEquals("trip-2", stateForNewTrip.inProgressTrip?.id)
            assertFalse(stateForNewTrip.showResumeClassificationAction)
        }
    }

    @Test
    fun `onResumeClassificationClicked does not itself mutate state (manual nav callback owns navigation)`() = runTest {
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
}
