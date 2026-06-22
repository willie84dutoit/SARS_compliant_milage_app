package com.mileagetracker.app.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.data.export.CsvFileWriter
import com.mileagetracker.app.data.export.CsvWriteResult
import com.mileagetracker.app.domain.export.CsvExportRules
import com.mileagetracker.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * T-001 blueprint §5, row 6: export-in-progress flag, last export result. The ViewModel never
 * touches file I/O directly — [CsvFileWriter] is injected as a data-layer collaborator and
 * called only after [CsvExportRules.buildExportRows] (domain) has filtered the rows, per the
 * blueprint's hard rule that file I/O stays out of the ViewModel's own logic surface.
 */
sealed interface ExportResult {
    data object Idle : ExportResult
    data class Success(val filename: String, val rowCount: Int) : ExportResult
    data class Failure(val message: String) : ExportResult
}

data class ExportUiState(
    val isExportInProgress: Boolean = false,
    val lastExportResult: ExportResult = ExportResult.Idle,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val csvFileWriter: CsvFileWriter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun onExportRequested() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExportInProgress = true)
            val completedTrips = tripRepository.getCompletedTripsForExport()
            val exportRows = CsvExportRules.buildExportRows(completedTrips)
            val writeResult = withContext(Dispatchers.IO) {
                csvFileWriter.writeToDownloads(exportRows)
            }
            val exportResult = when (writeResult) {
                is CsvWriteResult.Success -> ExportResult.Success(writeResult.filename, exportRows.size)
                is CsvWriteResult.Failure -> ExportResult.Failure(writeResult.message)
            }
            _uiState.value = ExportUiState(
                isExportInProgress = false,
                lastExportResult = exportResult,
            )
        }
    }
}
