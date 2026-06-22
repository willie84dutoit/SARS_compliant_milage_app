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
 */
data class TripHistoryUiState(
    val completedTrips: List<Trip> = emptyList(),
    val pendingBusinessReasonTrips: List<Trip> = emptyList(),
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    tripRepository: TripRepository,
) : ViewModel() {

    val uiState: StateFlow<TripHistoryUiState> = combine(
        tripRepository.observeTripHistory(),
        tripRepository.observePendingBusinessReasonTrips(),
    ) { completedTrips, pendingTrips ->
        TripHistoryUiState(completedTrips = completedTrips, pendingBusinessReasonTrips = pendingTrips)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = TripHistoryUiState(),
    )
}
