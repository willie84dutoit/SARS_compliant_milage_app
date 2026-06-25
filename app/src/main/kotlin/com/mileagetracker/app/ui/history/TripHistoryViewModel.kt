package com.mileagetracker.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * T-001 blueprint §5, row 5: completed + pending trips merged for display, with pending ones
 * flagged distinctly per brief §5.9's "clearly marked in the UI" requirement.
 *
 * M-2 fix: [pendingOcrTrips] shows trips in PENDING_OCR status (stopped but not yet classified/
 * odometer-confirmed) so they are visible in History and not silently hidden while the user
 * navigates away from the classification/odometer flow.
 */
data class TripHistoryUiState(
    val completedTrips: List<Trip> = emptyList(),
    val pendingBusinessReasonTrips: List<Trip> = emptyList(),
    val pendingOcrTrips: List<Trip> = emptyList(),
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository,
) : ViewModel() {

    val uiState: StateFlow<TripHistoryUiState> = combine(
        tripRepository.observeTripHistory(),
        tripRepository.observePendingBusinessReasonTrips(),
        tripRepository.observeInProgressTrip(),
    ) { completedTrips, pendingBusinessReasonTrips, inProgressTrip ->
        // M-2 fix: extract PENDING_OCR trips from the in-progress observable. The DAO's
        // observeInProgressTrip() returns the latest trip in (active, pending_ocr,
        // pending_business_reason) — if it's PENDING_OCR it should surface in History as
        // "Awaiting odometer / classification" rather than being invisible.
        val pendingOcrTrips = if (
            inProgressTrip != null &&
            inProgressTrip.status == com.mileagetracker.app.domain.model.TripStatus.PENDING_OCR
        ) {
            listOf(inProgressTrip)
        } else {
            emptyList()
        }
        TripHistoryUiState(
            completedTrips = completedTrips,
            pendingBusinessReasonTrips = pendingBusinessReasonTrips,
            pendingOcrTrips = pendingOcrTrips,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = TripHistoryUiState(),
    )
}
