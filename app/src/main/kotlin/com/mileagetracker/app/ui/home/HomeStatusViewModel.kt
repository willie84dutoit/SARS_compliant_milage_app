package com.mileagetracker.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.TripRepository
import com.mileagetracker.app.service.TripTrackingForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

/**
 * T-001 blueprint §5, row 2: in-progress trip + last-completed-trip summary.
 *
 * [autoRoutedToClassificationTripId] is the T-022 back-loop fix: records which trip id
 * [HomeStatusScreen]'s `LaunchedEffect` has already auto-navigated to Trip Classification for.
 * Without this gate, every recomposition of Home while the same trip is still [TripStatus.PENDING_OCR]
 * (e.g. immediately after the user presses system Back off the Classification screen) re-fired
 * the auto-navigation. See [onTripClassificationAutoRouted] and [onResumeClassificationClicked].
 *
 * C-2 note: the former [pendingOdometerNavigationTripId] gate and all related methods
 * (onClassificationSavedNavigatingToOdometer, onResumeOdometerClicked) are removed.
 * The two-screen gap they guarded (Classification saved, trip still PENDING_OCR while navigating
 * to OdometerCapture) no longer exists — classification and odometer are now one atomic Save on
 * the merged TripClassificationScreen.
 */
data class HomeStatusUiState(
    val inProgressTrip: Trip? = null,
    val lastCompletedTrip: Trip? = null,
    val autoRoutedToClassificationTripId: String? = null,
) {
    /** True only while a trip is actively GPS-tracked (not while pending classification). */
    val isTrackingActive: Boolean get() = inProgressTrip?.status == TripStatus.ACTIVE

    /**
     * True when there is a trip awaiting classification that the user has already backed out of
     * once — Home shows "Resume classification" instead of silently stranding the trip. Work trips
     * cannot export without a business reason (locked v1 fact).
     */
    val showResumeClassificationAction: Boolean
        get() = inProgressTrip != null &&
            inProgressTrip.status == TripStatus.PENDING_OCR &&
            inProgressTrip.id == autoRoutedToClassificationTripId
}

/**
 * Owns the manual start/stop control surface. Starting/stopping the foreground service is a
 * platform action — the actual trip lifecycle lives entirely in [TripTrackingForegroundService],
 * never here, per blueprint §6.1 boundary rule.
 */
@HiltViewModel
class HomeStatusViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tripRepository: TripRepository,
) : ViewModel() {

    /** T-022 back-loop-fix state — see [HomeStatusUiState.autoRoutedToClassificationTripId]. */
    private val autoRoutedToClassificationTripId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeStatusUiState> = combine(
        tripRepository.observeInProgressTrip(),
        tripRepository.observeTripHistory(),
        autoRoutedToClassificationTripId,
    ) { inProgressTrip, history, autoRoutedTripId ->
        HomeStatusUiState(
            inProgressTrip = inProgressTrip,
            lastCompletedTrip = history.firstOrNull(),
            autoRoutedToClassificationTripId = autoRoutedTripId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeStatusUiState(),
    )

    fun onStartTripClicked() {
        Timber.tag("MT-UI").i("HomeStatusScreen: Start trip button clicked")
        val startIntent = Intent(appContext, TripTrackingForegroundService::class.java).apply {
            action = TripTrackingForegroundService.ACTION_START_TRIP
        }
        Timber.tag("MT-Trip").i(
            "Dispatching ACTION_START_TRIP to TripTrackingForegroundService; the trip insert " +
                "(TripRepository.insertNewActiveTrip) happens inside the service, not here — see " +
                "MT-Service/MT-Repository log lines for the resulting DB write",
        )
        ContextCompat.startForegroundService(appContext, startIntent)
    }

    fun onStopTripClicked() {
        val inProgressTripId = uiState.value.inProgressTrip?.id
        Timber.tag("MT-UI").i(
            "HomeStatusScreen: Stop trip button clicked for tripId=%s",
            inProgressTripId,
        )
        val stopIntent = Intent(appContext, TripTrackingForegroundService::class.java).apply {
            action = TripTrackingForegroundService.ACTION_STOP_TRIP
        }
        Timber.tag("MT-Trip").i(
            "Dispatching ACTION_STOP_TRIP to TripTrackingForegroundService for tripId=%s; the " +
                "status transition to PENDING_OCR (TripRepository.updateStatus) happens inside " +
                "the service — see MT-Service/MT-Repository log lines for the resulting DB write",
            inProgressTripId,
        )
        ContextCompat.startForegroundService(appContext, stopIntent)
    }

    /**
     * T-022 back-loop fix: called by [HomeStatusScreen] exactly once per trip, the first time it
     * auto-navigates to Trip Classification for [tripId]. Recorded here so the gate survives
     * screen recomposition/recreation, and so a second auto-navigation attempt for the same
     * still-PENDING_OCR trip is provably a no-op.
     */
    fun onTripClassificationAutoRouted(tripId: String) {
        Timber.tag("MT-UI").i(
            "HomeStatusScreen: auto-navigated to TripClassification for tripId=%s " +
                "(first time — will not auto-navigate again for this trip)",
            tripId,
        )
        autoRoutedToClassificationTripId.value = tripId
    }

    /**
     * T-022 back-loop fix: manual escape hatch. After the user backs out of Classification once,
     * [HomeStatusUiState.showResumeClassificationAction] is true and Home shows a "Resume
     * classification" button. This method is called on that tap — navigation itself is handled
     * by the caller.
     */
    fun onResumeClassificationClicked() {
        val inProgressTripId = uiState.value.inProgressTrip?.id
        Timber.tag("MT-UI").i(
            "HomeStatusScreen: Resume classification button clicked for tripId=%s",
            inProgressTripId,
        )
    }
}
