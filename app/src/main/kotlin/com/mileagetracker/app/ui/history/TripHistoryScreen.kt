package com.mileagetracker.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Trip History screen shell (brief §7 #5). */
@Composable
fun TripHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TripHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            Button(onClick = onNavigateBack) { Text("Back") }

            if (uiState.pendingBusinessReasonTrips.isNotEmpty()) {
                Text("Pending business reason (${uiState.pendingBusinessReasonTrips.size})")
                LazyColumn {
                    items(uiState.pendingBusinessReasonTrips) { trip ->
                        Text("PENDING: ${trip.id} — ${trip.distanceKm} km")
                    }
                }
            }

            Text("Completed trips")
            LazyColumn {
                items(uiState.completedTrips) { trip ->
                    Text("${trip.id} — ${trip.classification} — ${trip.distanceKm} km")
                }
            }
        }
    }
}
