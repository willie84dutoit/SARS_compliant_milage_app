package com.mileagetracker.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mileagetracker.app.domain.model.TripStatus

/**
 * Home/Status screen shell (brief §7 #2). Owns the manual Start/Stop trip control for this MVP
 * build (T-002's automatic ActivityRecognition start is the eventual primary trigger). When the
 * in-progress trip transitions to PENDING_OCR (the foreground service's stop-event landing state,
 * blueprint §4), this screen immediately routes to Trip Classification — the trip is never left
 * stranded in PENDING_OCR within the same app session.
 */
@Composable
fun HomeStatusScreen(
    onNavigateToTripHistory: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onTripAwaitingClassification: (tripId: String) -> Unit,
    viewModel: HomeStatusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.inProgressTrip?.id, uiState.inProgressTrip?.status) {
        val inProgressTrip = uiState.inProgressTrip
        if (inProgressTrip != null && inProgressTrip.status == TripStatus.PENDING_OCR) {
            onTripAwaitingClassification(inProgressTrip.id)
        }
    }

    Scaffold { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            Text(
                when {
                    uiState.isTrackingActive -> "Trip in progress — ${"%.2f".format(uiState.inProgressTrip?.distanceKm ?: 0.0)} km"
                    uiState.inProgressTrip != null -> "Finishing up last trip..."
                    else -> "No trip in progress"
                },
            )
            uiState.lastCompletedTrip?.let { lastTrip ->
                Text("Last trip: ${lastTrip.distanceKm} km, ${lastTrip.classification}")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.inProgressTrip == null) {
                    Button(onClick = viewModel::onStartTripClicked) { Text("Start trip") }
                } else if (uiState.isTrackingActive) {
                    Button(onClick = viewModel::onStopTripClicked) { Text("Stop trip") }
                }
                Button(onClick = onNavigateToTripHistory) { Text("Trip history") }
                Button(onClick = onNavigateToExport) { Text("Export") }
                Button(onClick = onNavigateToSettings) { Text("Settings") }
            }
        }
    }
}
