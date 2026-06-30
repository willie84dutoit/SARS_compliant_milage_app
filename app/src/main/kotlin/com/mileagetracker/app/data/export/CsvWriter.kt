package com.mileagetracker.app.data.export

import com.mileagetracker.app.domain.export.CsvRow
import com.mileagetracker.app.domain.model.Trip

/**
 * Abstraction over MediaStore export I/O so [com.mileagetracker.app.ui.export.ExportViewModel] can
 * be tested without an Android context. The single production implementation is [CsvFileWriter].
 *
 * T-032 Half A / Pass 2: widened from CSV-only to also write the integrity sidecar and the static
 * verification recipe through the SAME export call — one shared file sink, not a second I/O
 * surface — so the three files always share one MediaStore write pass and one filename stem. The
 * ViewModel passes both [trips] (the [Trip] domain objects, needed by
 * [com.mileagetracker.app.domain.export.IntegritySidecarGenerator] for the signing fields) and
 * [rows] (the already-built [CsvRow]s for the CSV body) — CSV row order is irrelevant to the
 * sidecar, which sorts internally by `tripSequenceNumber`.
 */
fun interface CsvWriter {
    fun writeExport(trips: List<Trip>, rows: List<CsvRow>): ExportWriteResult
}
