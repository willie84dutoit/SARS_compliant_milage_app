package com.mileagetracker.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.ui.common.MileageTrackerScaffold

/** Settings screen shell (brief §7 #7). */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    MileageTrackerScaffold(
        screenTitle = "Settings",
        navigationIconContent = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            Row {
                Text("Save odometer photos")
                Switch(
                    checked = uiState.photoRetentionMode == PhotoRetentionMode.SAVED,
                    onCheckedChange = viewModel::onPhotoRetentionToggled,
                )
            }

            Row {
                Column {
                    Text("Bluetooth vehicle trigger (advanced)")
                    Text("Coming soon", modifier = Modifier.padding(top = 4.dp))
                }
                Switch(
                    checked = uiState.isBluetoothVehicleTriggerEnabled,
                    enabled = false,
                    onCheckedChange = {},
                )
            }

            // T-030 P0.4 Option A (debug-only): the export button and its result feedback are
            // hidden entirely in release builds — the row is unreachable by end-users.
            // PII-redaction / FileProvider-share deferred to a pre-Play-Store task (T-038 / T-030 P0.4 follow-up).
            if (uiState.isDebugLogExportAvailable) {
                Button(onClick = viewModel::onExportDebugLogClicked) {
                    Text("Export debugging logs")
                }

                when (val result = uiState.lastDebugLogExportResult) {
                    is DebugLogExportUiResult.Success -> Text("Debug log exported to ${result.filename}")
                    is DebugLogExportUiResult.Failure -> Text("Debug log export failed: ${result.message}")
                    DebugLogExportUiResult.Idle -> Unit
                }
            }
        }
    }
}
