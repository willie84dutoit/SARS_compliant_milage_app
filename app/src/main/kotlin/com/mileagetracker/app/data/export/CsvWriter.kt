package com.mileagetracker.app.data.export

import com.mileagetracker.app.domain.export.CsvRow

/**
 * Abstraction over MediaStore CSV I/O so [com.mileagetracker.app.ui.export.ExportViewModel] can
 * be tested without an Android context. The single production implementation is [CsvFileWriter].
 */
fun interface CsvWriter {
    fun writeToDownloads(rows: List<CsvRow>): CsvWriteResult
}
