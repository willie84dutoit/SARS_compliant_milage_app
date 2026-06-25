package com.mileagetracker.app.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.data.export.CsvWriter
import com.mileagetracker.app.data.export.CsvWriteResult
import com.mileagetracker.app.domain.export.CsvExportRules
import com.mileagetracker.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * T-001 blueprint §5, row 6: export-in-progress flag, last export result. The ViewModel never
 * touches file I/O directly — [CsvFileWriter] is injected as a data-layer collaborator and
 * called only after [CsvExportRules.buildExportRows] (domain) has filtered the rows, per the
 * blueprint's hard rule that file I/O stays out of the ViewModel's own logic surface.
 *
 * M-3 fix: [ExportUiState.completedTripCount] is populated reactively from
 * [TripRepository.observeTripHistory] so the screen can disable the Export button and show a
 * meaningful empty-state message when there are no completed trips to export.
 */
sealed interface ExportResult {
    data object Idle : ExportResult
    data class Success(val filename: String, val rowCount: Int) : ExportResult
    data class Failure(val message: String) : ExportResult
}

data class ExportUiState(
    val isExportInProgress: Boolean = false,
    val lastExportResult: ExportResult = ExportResult.Idle,
    /** M-3 fix: number of completed trips available for export. */
    val completedTripCount: Int = 0,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val csvWriter: CsvWriter,
) : ViewModel() {

    private val exportProgressAndResult = MutableStateFlow(
        ExportUiState(isExportInProgress = false, lastExportResult = ExportResult.Idle),
    )

    // M-3 fix: combine the mutable export-progress state with the reactive trip-history count so
    // the button disabled state is always in sync with the actual DB state, not a one-shot suspend
    // call that could go stale.
    val uiState: StateFlow<ExportUiState> = combine(
        exportProgressAndResult,
        tripRepository.observeTripHistory(),
    ) { progressState, completedTrips ->
        progressState.copy(completedTripCount = completedTrips.size)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ExportUiState(),
    )

    fun onExportRequested() {
        Timber.tag("MT-UI").i("ExportScreen: Export button clicked")
        viewModelScope.launch {
            exportProgressAndResult.value = exportProgressAndResult.value.copy(isExportInProgress = true)
            val completedTrips = tripRepository.getCompletedTripsForExport()
            val exportRows = CsvExportRules.buildExportRows(completedTrips)
            val writeResult = withContext(Dispatchers.IO) {
                csvWriter.writeToDownloads(exportRows)
            }
            val exportResult = when (writeResult) {
                is CsvWriteResult.Success -> ExportResult.Success(writeResult.filename, exportRows.size)
                is CsvWriteResult.Failure -> ExportResult.Failure(writeResult.message)
            }
            exportProgressAndResult.value = ExportUiState(
                isExportInProgress = false,
                lastExportResult = exportResult,
            )
        }
    }
}
