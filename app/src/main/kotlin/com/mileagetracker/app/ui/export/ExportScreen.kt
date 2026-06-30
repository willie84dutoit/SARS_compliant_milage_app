package com.mileagetracker.app.ui.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mileagetracker.app.ui.common.MileageTrackerScaffold

/** Export screen shell (brief §7 #6). */
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    MileageTrackerScaffold(
        screenTitle = "Export",
        navigationIconContent = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            // M-3 fix: disable Export when there are no completed trips and show a clear empty
            // state so the button is never silently "enabled but nothing to export".
            val hasCompletedTrips = uiState.completedTripCount > 0

            if (!hasCompletedTrips && uiState.lastExportResult == ExportResult.Idle) {
                Text(
                    text = "No completed trips to export yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Complete at least one trip (classify + confirm odometer) before exporting.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = viewModel::onExportRequested,
                enabled = !uiState.isExportInProgress && hasCompletedTrips,
            ) { Text("Export completed trips to CSV") }

            if (uiState.isExportInProgress) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }

            when (val result = uiState.lastExportResult) {
                is ExportResult.Success -> {
                    if (result.integrityWarning != null) {
                        Text("Exported ${result.rowCount} trips to ${result.filename}")
                        Text(result.integrityWarning, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(
                            "Exported ${result.rowCount} trips + integrity file to ${result.filename}",
                        )
                    }
                }
                is ExportResult.Failure -> Text("Export failed: ${result.message}")
                ExportResult.Idle -> Unit
            }
        }
    }
}
