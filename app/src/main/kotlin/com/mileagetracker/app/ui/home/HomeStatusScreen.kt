package com.mileagetracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import com.mileagetracker.app.ui.common.MileageTrackerScaffold

/**
 * Home/Status screen (brief §7 #2). Owns the manual Start/Stop trip control. When the in-progress
 * trip transitions to PENDING_OCR this screen routes to Trip Classification automatically once.
 *
 * T-022 back-loop fix: the auto-navigation fires at most once per trip id, gated by
 * [HomeStatusUiState.autoRoutedToClassificationTripId]. After Back off the Classification screen,
 * [HomeStatusUiState.showResumeClassificationAction] surfaces an explicit "Resume classification"
 * button so the trip is never silently stranded.
 *
 * C-2 note: the former isPendingOdometerCapture guard and "Resume odometer capture" button are
 * removed. Classification and odometer are now one atomic Save; the two-screen gap that required
 * the C-2 gate no longer exists.
 *
 * H-1 fix: banner label distinguishes manual starts ("Trip active (manual)") from auto-detected
 * trips ("Trip detected") using [Trip.isManualStart].
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
        if (
            inProgressTrip != null &&
            inProgressTrip.status == TripStatus.PENDING_OCR &&
            !alreadyAutoRouted
        ) {
            viewModel.onTripClassificationAutoRouted(inProgressTrip.id)
            onTripAwaitingClassification(inProgressTrip.id)
        }
    }

    MileageTrackerScaffold(screenTitle = "Mileage Tracker") { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            val hasTripActivity = uiState.inProgressTrip != null
            // H-1 fix: label text carries the origin signal (manual vs auto-detected).
            val bannerText = when {
                uiState.isTrackingActive && uiState.inProgressTrip?.isManualStart == true ->
                    "Trip active (manual)"
                uiState.isTrackingActive ->
                    "Trip detected"
                hasTripActivity ->
                    "Finishing up..."
                else ->
                    "No trip active"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(bottom = 16.dp)
                    .background(
                        color = if (hasTripActivity) Color(0xFF2E7D32) else Color(0xFFC62828),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bannerText,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                when {
                    uiState.isTrackingActive ->
                        "Trip in progress — ${"%.2f".format(uiState.inProgressTrip?.distanceKm ?: 0.0)} km"
                    uiState.showResumeClassificationAction ->
                        "Last trip still needs classification"
                    uiState.inProgressTrip != null ->
                        "Finishing up last trip..."
                    else ->
                        "No trip in progress"
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
                    // T-022 back-loop fix: explicit manual re-entry after the user backed out.
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
