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
import javax.inject.Inject

/** T-001 blueprint §5, row 2: in-progress trip + last-completed-trip summary. */
data class HomeStatusUiState(
    val inProgressTrip: Trip? = null,
    val lastCompletedTrip: Trip? = null,
) {
    /** True only while a trip is actively being GPS-tracked (not while pending classification/odometer). */
    val isTrackingActive: Boolean get() = inProgressTrip?.status == TripStatus.ACTIVE
}

/**
 * Owns the manual start/stop control surface (this MVP build's primary trip trigger, ahead of
 * T-002's automatic ActivityRecognition start). Starting/stopping the foreground service is a
 * platform action, not business logic — the actual trip lifecycle (insert/accumulate/stop-timers)
 * lives entirely in [TripTrackingForegroundService], never here, per the blueprint §6.1 boundary
 * rule that ViewModels hold no DB/service logic themselves.
 */
@HiltViewModel
class HomeStatusViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    tripRepository: TripRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeStatusUiState> = combine(
        tripRepository.observeInProgressTrip(),
        tripRepository.observeTripHistory(),
    ) { inProgressTrip, history ->
        HomeStatusUiState(inProgressTrip = inProgressTrip, lastCompletedTrip = history.firstOrNull())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = HomeStatusUiState(),
    )

    fun onStartTripClicked() {
        val startIntent = Intent(appContext, TripTrackingForegroundService::class.java).apply {
            action = TripTrackingForegroundService.ACTION_START_TRIP
        }
        ContextCompat.startForegroundService(appContext, startIntent)
    }

    fun onStopTripClicked() {
        val stopIntent = Intent(appContext, TripTrackingForegroundService::class.java).apply {
            action = TripTrackingForegroundService.ACTION_STOP_TRIP
        }
        ContextCompat.startForegroundService(appContext, stopIntent)
    }
}
