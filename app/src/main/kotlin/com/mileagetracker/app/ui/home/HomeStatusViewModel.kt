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
 * [autoRoutedToClassificationTripId] is the T-022 back-loop fix (team/TASKS.md T-022 card):
 * it records which trip id [HomeStatusScreen]'s `LaunchedEffect` has already auto-navigated to
 * Trip Classification for. Without this, every recomposition of Home while the same trip is
 * still [TripStatus.PENDING_OCR] (e.g. immediately after the user presses system Back off the
 * Classification screen) re-fired the auto-navigation and made Back feel modal/blocked. See
 * [onTripClassificationAutoRouted] and [onResumeClassificationClicked].
 */
data class HomeStatusUiState(
    val inProgressTrip: Trip? = null,
    val lastCompletedTrip: Trip? = null,
    val autoRoutedToClassificationTripId: String? = null,
) {
    /** True only while a trip is actively being GPS-tracked (not while pending classification/odometer). */
    val isTrackingActive: Boolean get() = inProgressTrip?.status == TripStatus.ACTIVE

    /**
     * True when there is a trip awaiting classification that the user has already backed out of
     * once (i.e. auto-navigation already fired for it) — Home must show a manual way back in
     * rather than silently stranding the trip, since Work trips cannot export without a business
     * reason (locked v1 fact) and the trip cannot self-resolve without the user finishing
     * classification.
     */
    val showResumeClassificationAction: Boolean
        get() = inProgressTrip != null &&
            inProgressTrip.status == TripStatus.PENDING_OCR &&
            inProgressTrip.id == autoRoutedToClassificationTripId
}

/**
 * Owns the manual start/stop control surface (this MVP build's primary trip trigger, ahead of
 * T-002's automatic ActivityRecognition start). Starting/stopping the foreground service is a
 * platform action, not business logic — the actual trip lifecycle (insert/accumulate/stop-timers)
 * lives entirely in [TripTrackingForegroundService], never here, per the blueprint §6.1 boundary
 * rule that ViewModels hold no DB/service logic themselves.
 *
 * T-022 reference pattern (team/blueprints/T-022-audit-logging-spec.md): this class is the
 * worked example for the `MT-UI` (user-initiated action) / `MT-Trip` (DB read/write tied to that
 * action) audit-logging convention. Read the spec file before replicating this pattern into
 * `TripClassificationViewModel` or `OdometerCaptureViewModel`.
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
     * auto-navigates to Trip Classification for [tripId]. Recorded here (not just as a local
     * `remember` in the screen) so the gate survives the screen's own recomposition/recreation,
     * and so a second auto-navigation attempt for the same still-PENDING_OCR trip is provably a
     * no-op rather than a repeat of the original bug.
     */
    fun onTripClassificationAutoRouted(tripId: String) {
        Timber.tag("MT-UI").i(
            "HomeStatusScreen: auto-navigated to TripClassification for tripId=%s (first time " +
                "this session — will not auto-navigate again for this trip)",
            tripId,
        )
        autoRoutedToClassificationTripId.value = tripId
    }

    /**
     * T-022 back-loop fix: the manual escape hatch. After the user backs out of Classification
     * once, [HomeStatusUiState.showResumeClassificationAction] becomes true and Home shows a
     * "Resume classification" action instead of silently re-trapping the user — this is the
     * explicit, user-initiated re-entry into the same auto-navigation callback used the first
     * time, so it is logged under the same tags for a complete trail.
     */
    fun onResumeClassificationClicked() {
        val inProgressTripId = uiState.value.inProgressTrip?.id
        Timber.tag("MT-UI").i(
            "HomeStatusScreen: Resume classification button clicked for tripId=%s",
            inProgressTripId,
        )
    }
}
