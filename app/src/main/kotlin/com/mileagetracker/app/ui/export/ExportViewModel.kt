package com.mileagetracker.app.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.data.export.CsvWriter
import com.mileagetracker.app.data.export.ExportWriteResult
import com.mileagetracker.app.domain.export.CsvExportRules
import com.mileagetracker.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
 *
 * T-039 fix (count-vs-export TOCTOU): [ExportUiState.completedTripCount] is now the number of
 * rows [CsvExportRules.buildExportRows] would actually produce from the observed history, not
 * the raw count of COMPLETED-status trips. [CsvExportRules.buildExportRows] applies a defensive
 * second filter beyond status == COMPLETED (a WORK trip's business reason must be non-blank,
 * see [com.mileagetracker.app.domain.classification.ClassificationRules.isBusinessReasonSatisfied]),
 * so a raw status count could previously disagree with the true exportable row count and leave
 * the button enabled while the export produced zero rows. Routing both the count and the actual
 * export through the same [CsvExportRules.buildExportRows] predicate keeps the two in sync by
 * construction — the predicate is defined exactly once in the domain layer.
 *
 * T-032 Half A / Pass 2: [Success] now also reports the integrity sidecar filenames (when
 * written) and [Success.integrityWarning] (the fallback-with-warning path — CSV exported fine,
 * but no sidecar could be generated because the signing public key was unreadable).
 */
sealed interface ExportResult {
    data object Idle : ExportResult
    data class Success(
        val filename: String,
        val rowCount: Int,
        val sidecarJsonFilename: String? = null,
        val verifyMarkdownFilename: String? = null,
        val integrityWarning: String? = null,
    ) : ExportResult
    data class Failure(val message: String) : ExportResult
}

data class ExportUiState(
    val isExportInProgress: Boolean = false,
    val lastExportResult: ExportResult = ExportResult.Idle,
    /**
     * T-039 fix: number of rows [CsvExportRules.buildExportRows] would actually produce from the
     * currently observed trip history — i.e. the true exportable count, not a raw COMPLETED-status
     * count. See the class-level doc comment for why this distinction matters.
     */
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
    // T-039 fix: the count is derived via CsvExportRules.buildExportRows — the exact same
    // predicate onExportRequested() uses below — instead of a raw completedTrips.size, so the
    // button-enabled count can never disagree with the real export row count.
    val uiState: StateFlow<ExportUiState> = combine(
        exportProgressAndResult,
        tripRepository.observeTripHistory(),
    ) { progressState, completedTrips ->
        progressState.copy(completedTripCount = CsvExportRules.buildExportRows(completedTrips).size)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ExportUiState(),
    )

    fun onExportRequested() {
        Timber.tag("MT-UI").i("ExportScreen: Export button clicked")
        viewModelScope.launch {
            exportProgressAndResult.update { it.copy(isExportInProgress = true) }
            val completedTrips = tripRepository.getCompletedTripsForExport()
            val exportRows = CsvExportRules.buildExportRows(completedTrips)
            val writeResult = withContext(Dispatchers.IO) {
                csvWriter.writeExport(completedTrips, exportRows)
            }
            val exportResult = when (writeResult) {
                is ExportWriteResult.Success -> ExportResult.Success(
                    filename = writeResult.csvFilename,
                    rowCount = exportRows.size,
                    sidecarJsonFilename = writeResult.sidecarJsonFilename,
                    verifyMarkdownFilename = writeResult.verifyMarkdownFilename,
                    integrityWarning = writeResult.integrityWarning,
                )
                is ExportWriteResult.Failure -> ExportResult.Failure(writeResult.message)
            }
            exportProgressAndResult.update {
                it.copy(isExportInProgress = false, lastExportResult = exportResult)
            }
        }
    }
}
