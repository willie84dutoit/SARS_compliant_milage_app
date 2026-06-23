package com.mileagetracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mileagetracker.app.domain.model.TripStatus

/**
 * Home/Status screen shell (brief §7 #2). Owns the manual Start/Stop trip control for this MVP
 * build (T-002's automatic ActivityRecognition start is the eventual primary trigger). When the
 * in-progress trip transitions to PENDING_OCR (the foreground service's stop-event landing state,
 * blueprint §4), this screen routes to Trip Classification automatically **once** per trip.
 *
 * T-022 back-loop fix (team/TASKS.md T-022 card): the auto-navigation used to re-fire on every
 * recomposition where the trip was still PENDING_OCR — which made pressing system Back off
 * Classification feel modal, since Back popped to Home and Home immediately bounced straight
 * back to Classification. [HomeStatusUiState.autoRoutedToClassificationTripId] now gates this
 * `LaunchedEffect` to fire at most once per trip id; after that, [HomeStatusUiState.showResumeClassificationAction]
 * surfaces an explicit "Resume classification" button instead, so the trip is never silently
 * stranded in PENDING_OCR with no way back in (Work trips need a non-empty business reason
 * before they can complete/export — locked v1 fact).
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
        val alreadyAutoRouted = inProgressTrip != null &&
            inProgressTrip.id == uiState.autoRoutedToClassificationTripId
        if (inProgressTrip != null && inProgressTrip.status == TripStatus.PENDING_OCR && !alreadyAutoRouted) {
            viewModel.onTripClassificationAutoRouted(inProgressTrip.id)
            onTripAwaitingClassification(inProgressTrip.id)
        }
    }

    Scaffold { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            val isDetected = uiState.inProgressTrip != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(bottom = 16.dp)
                    .background(
                        color = if (isDetected) Color(0xFF2E7D32) else Color(0xFFC62828),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isDetected) "Detected" else "Not detected",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                when {
                    uiState.isTrackingActive -> "Trip in progress — ${"%.2f".format(uiState.inProgressTrip?.distanceKm ?: 0.0)} km"
                    uiState.showResumeClassificationAction -> "Last trip still needs classification"
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
                } else if (uiState.showResumeClassificationAction) {
                    // T-022 back-loop fix: explicit manual re-entry so a trip the user backed out
                    // of once is never stranded in PENDING_OCR with no way to finish classifying it.
                    Button(
                        onClick = {
                            viewModel.onResumeClassificationClicked()
                            uiState.inProgressTrip?.id?.let(onTripAwaitingClassification)
                        },
                    ) { Text("Resume classification") }
                }
                Button(onClick = onNavigateToTripHistory) { Text("Trip history") }
                Button(onClick = onNavigateToExport) { Text("Export") }
                Button(onClick = onNavigateToSettings) { Text("Settings") }
            }
        }
    }
}
