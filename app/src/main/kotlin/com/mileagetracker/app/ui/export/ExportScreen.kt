package com.mileagetracker.app.ui.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Export screen shell (brief §7 #6). */
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            Button(onClick = onNavigateBack) { Text("Back") }
            Button(onClick = viewModel::onExportRequested) { Text("Export completed trips to CSV") }

            when (val result = uiState.lastExportResult) {
                is ExportResult.Success -> Text("Exported ${result.rowCount} trips to ${result.filename}")
                is ExportResult.Failure -> Text("Export failed: ${result.message}")
                ExportResult.Idle -> Unit
            }
        }
    }
}
