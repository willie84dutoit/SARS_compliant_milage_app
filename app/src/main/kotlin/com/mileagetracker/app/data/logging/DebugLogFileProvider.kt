package com.mileagetracker.app.data.logging

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Result of a [DebugLogFileProvider.exportDebugLogToDownloads] call — a result type, not an
 * exception, so the caller (here, `SettingsViewModel`) can render a failure as UI state instead
 * of needing its own try/catch around every export attempt (T-018 reference pattern: data-layer
 * I/O boundaries return a typed result; only the boundary itself catches exceptions).
 */
sealed interface DebugLogExportResult {
    data class Success(val filename: String) : DebugLogExportResult
    data class Failure(val message: String) : DebugLogExportResult
}

/**
 * Exports the debug log to the Downloads folder. Reads both the active log file
 * (`mileage_tracker_log.txt`) and its rotated predecessor (`.1`), concatenating them so the
 * export covers the full rotation window. Uses MediaStore on Android 10+ (this app's minSdk)
 * rather than direct File I/O against the public Downloads directory, since scoped storage
 * applies from API 29 onward.
 *
 * This class is the ONLY boundary that touches File I/O for log export. [SettingsViewModel]
 * injects it and calls it; the ViewModel stays free of direct file operations.
 */
class DebugLogFileProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private val filenameTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * T-018 reference try/catch pattern: this is the boundary between this app's code and a
     * platform I/O surface (MediaStore/ContentResolver/File) that a field tester's device can
     * fail in ways we cannot fully enumerate (storage full, Downloads provider missing on a
     * custom ROM, permission revoked mid-session, log file inaccessible). Every failure is
     * logged under MT-Export with enough detail to diagnose remotely from the exported debug log,
     * and surfaced as [DebugLogExportResult.Failure] rather than thrown — `SettingsViewModel`
     * renders that directly as user-facing UI state instead of crashing the settings flow.
     * [CancellationException] is the sole exception still rethrown, per structured-concurrency
     * contract (cancelling export, e.g. leaving the Settings screen, must not be reported as an
     * export failure).
     */
    fun exportDebugLogToDownloads(): DebugLogExportResult {
        val filename = "mileage_tracker_debug_log_${filenameTimestampFormat.format(Date())}.txt"
        return try {
            val logContent = readLogFiles()

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val downloadsCollection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val fileUri = requireNotNull(appContext.contentResolver.insert(downloadsCollection, contentValues)) {
                "MediaStore failed to create a Downloads entry for $filename"
            }

            appContext.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                outputStream.write(logContent.toByteArray(Charsets.UTF_8))
            } ?: error("MediaStore returned a null OutputStream for $filename")

            DebugLogExportResult.Success(filename)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exportFailure: Exception) {
            Timber.tag("MT-Export").e(exportFailure, "Failed to export debug log to Downloads as %s", filename)
            DebugLogExportResult.Failure(exportFailure.message ?: "Export failed: ${exportFailure::class.simpleName}")
        }
    }

    private fun readLogFiles(): String {
        val logsDirectory = File(appContext.filesDir, "logs")
        val rotatedLogFile = File(logsDirectory, "mileage_tracker_log.txt.1")
        val activeLogFile = File(logsDirectory, "mileage_tracker_log.txt")

        val contentBuilder = StringBuilder()

        // Rotated (older) file first, if it exists
        if (rotatedLogFile.exists()) {
            contentBuilder.append(rotatedLogFile.readText(Charsets.UTF_8))
        }

        // Active file second
        if (activeLogFile.exists()) {
            contentBuilder.append(activeLogFile.readText(Charsets.UTF_8))
        }

        return contentBuilder.toString()
    }
}
