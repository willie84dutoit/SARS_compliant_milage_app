package com.mileagetracker.app.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mileagetracker.app.domain.export.CsvRow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Result of a [CsvFileWriter.writeToDownloads] call — a result type, not an exception, so the
 * caller (here, `ExportViewModel`) can render a failure as UI state instead of needing its own
 * try/catch around every export attempt (T-018 reference pattern: data-layer I/O boundaries
 * return a typed result; only the boundary itself catches exceptions).
 */
sealed interface CsvWriteResult {
    data class Success(val filename: String) : CsvWriteResult
    data class Failure(val message: String) : CsvWriteResult
}

/**
 * Writes completed-trip [CsvRow]s to the Downloads folder. Filename pattern and encoding are
 * locked v1 facts: `mileage_trips_YYYYMMDD_HHMMSS.csv`, UTF-8, comma-separated, fixed column
 * order from [CsvRow.HEADER]. Uses MediaStore on Android 10+ (this app's minSdk) rather than
 * direct File I/O against the public Downloads directory, since scoped storage applies from
 * API 29 onward.
 */
class CsvFileWriter @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private val filenameTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * T-018 reference try/catch pattern: this is the boundary between this app's code and a
     * platform I/O surface (MediaStore/ContentResolver) that a field tester's device can fail in
     * ways we cannot fully enumerate (storage full, Downloads provider missing on a custom ROM,
     * permission revoked mid-session). Every failure is logged under MT-Export with enough detail
     * to diagnose remotely from the exported debug log (see [com.mileagetracker.app.data.logging.FileLoggingTree]),
     * and surfaced as [CsvWriteResult.Failure] rather than thrown — `ExportViewModel` renders that
     * directly as user-facing UI state instead of crashing the export flow.
     * [CancellationException] is the sole exception still rethrown, per structured-concurrency
     * contract (cancelling export, e.g. leaving the Export screen, must not be reported as a
     * write failure).
     */
    fun writeToDownloads(rows: List<CsvRow>): CsvWriteResult {
        val filename = "mileage_trips_${filenameTimestampFormat.format(Date())}.csv"
        return try {
            val csvContent = buildCsvContent(rows)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val downloadsCollection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val fileUri = requireNotNull(appContext.contentResolver.insert(downloadsCollection, contentValues)) {
                "MediaStore failed to create a Downloads entry for $filename"
            }

            appContext.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
            } ?: error("MediaStore returned a null OutputStream for $filename")

            CsvWriteResult.Success(filename)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (writeFailure: Exception) {
            Timber.tag("MT-Export").e(writeFailure, "Failed to write CSV export to Downloads as %s", filename)
            CsvWriteResult.Failure(writeFailure.message ?: "Export failed: ${writeFailure::class.simpleName}")
        }
    }

    private fun buildCsvContent(rows: List<CsvRow>): String {
        val builder = StringBuilder()
        builder.append(CsvRow.HEADER.joinToString(separator = ",")).append('\n')
        rows.forEach { row ->
            builder.append(
                listOf(
                    row.tripId,
                    row.classification,
                    row.startTimestamp.toString(),
                    row.endTimestamp.toString(),
                    row.startOdometerKm.toString(),
                    row.endOdometerKm.toString(),
                    row.verifiedOdometerKm?.toString().orEmpty(),
                    row.distanceKm.toString(),
                    escapeCsvField(row.businessReason.orEmpty()),
                    row.status,
                ).joinToString(separator = ","),
            ).append('\n')
        }
        return builder.toString()
    }

    /** Wraps a field in double quotes if it contains a comma, quote, or newline (RFC 4180). */
    private fun escapeCsvField(field: String): String {
        val needsQuoting = field.contains(',') || field.contains('"') || field.contains('\n')
        return if (needsQuoting) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
