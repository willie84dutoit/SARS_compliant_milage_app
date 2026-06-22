package com.mileagetracker.app.ui.odometer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Odometer Photo Capture screen shell (brief §7 #4). CameraX preview wiring lands in T-005 with
 * ml-ocr-specialist; this scaffold only shows the manual-entry fallback so the navigation graph
 * compiles end to end.
 */
@Composable
fun OdometerCaptureScreen(
    tripId: String,
    onCaptureComplete: () -> Unit,
    viewModel: OdometerCaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            Text("Capture odometer photo (camera preview pending T-005)")

            OutlinedTextField(
                value = uiState.manualEntryText,
                onValueChange = viewModel::onManualEntryChanged,
                label = { Text("Odometer reading (km)") },
            )

            Button(onClick = { viewModel.onConfirmManualOdometer(onCaptureComplete) }) {
                Text("Confirm")
            }
        }
    }
}
