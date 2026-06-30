package com.mileagetracker.app.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mileagetracker.app.BuildConfig
import com.mileagetracker.app.data.signing.TripSigner
import com.mileagetracker.app.domain.export.CsvRow
import com.mileagetracker.app.domain.export.ExportFilenamePlan
import com.mileagetracker.app.domain.export.IntegritySidecarGenerator
import com.mileagetracker.app.domain.export.VerificationRecipeDocument
import com.mileagetracker.app.domain.model.Trip
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** Implementation of [CsvWriter] — the only production handler. */

/**
 * Result of writing the CSV body alone — kept as a private intermediate so the public
 * [writeExport] contract ([ExportWriteResult]) stays the single externally-visible result type.
 */
private sealed interface CsvBodyWriteResult {
    data class Success(val filename: String) : CsvBodyWriteResult
    data class Failure(val message: String) : CsvBodyWriteResult
}

/**
 * Writes a completed export — the CSV plus, when possible, the integrity sidecar and its static
 * verification recipe — to the Downloads folder. Filename pattern and encoding are locked v1
 * facts: `mileage_trips_YYYYMMDD_HHMMSS.csv`, UTF-8, comma-separated, fixed column order from
 * [CsvRow.HEADER]. Uses MediaStore on Android 10+ (this app's minSdk) rather than direct File I/O
 * against the public Downloads directory, since scoped storage applies from API 29 onward.
 *
 * T-032 Half A / Pass 2: the CSV, `.integrity.json` sidecar, and `VERIFY.md` recipe are written
 * through this SAME class (shared file sink, no second I/O surface), all three sharing ONE
 * filename stem computed once per call via [ExportFilenamePlan]. The CSV write is the
 * must-not-fail artifact; the sidecar write degrades to a warning rather than failing the whole
 * export (Manager ruling — "helpful app, degrade never block").
 */
class CsvFileWriter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tripSigner: TripSigner,
    private val integritySidecarGenerator: IntegritySidecarGenerator,
) : CsvWriter {

    companion object {
        private const val TIMBER_TAG = "MT-Export"
        private const val SAST_ZONE_ID = "Africa/Johannesburg"
        private const val INTEGRITY_WARNING_MESSAGE =
            "Integrity file could not be generated — these records are not cryptographically verifiable."
    }

    private val filenameTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(SAST_ZONE_ID)
    }

    /**
     * T-018 reference try/catch pattern: this is the boundary between this app's code and a
     * platform I/O surface (MediaStore/ContentResolver) that a field tester's device can fail in
     * ways we cannot fully enumerate (storage full, Downloads provider missing on a custom ROM,
     * permission revoked mid-session). Every failure is logged under MT-Export with enough detail
     * to diagnose remotely from the exported debug log (see [com.mileagetracker.app.data.logging.FileLoggingTree]),
     * and surfaced as [ExportWriteResult.Failure] rather than thrown — `ExportViewModel` renders
     * that directly as user-facing UI state instead of crashing the export flow.
     * [CancellationException] is the sole exception still rethrown, per structured-concurrency
     * contract (cancelling export, e.g. leaving the Export screen, must not be reported as a
     * write failure).
     *
     * The CSV write happens first and is the only step that can produce [ExportWriteResult.Failure].
     * If it succeeds, the integrity sidecar is attempted; any failure there (including total
     * public-key read failure from [TripSigner.getSigningPublicKeyPem]) degrades to
     * [ExportWriteResult.Success] with [ExportWriteResult.Success.integrityWarning] set, never
     * blocking the already-successful CSV export.
     */
    override fun writeExport(trips: List<Trip>, rows: List<CsvRow>): ExportWriteResult {
        val timestampStem = filenameTimestampFormat.format(Date())
        val filenamePlan = ExportFilenamePlan.fromTimestampStem(timestampStem)

        val csvWriteResult = writeCsvBody(rows, filenamePlan.csvFilename)
        if (csvWriteResult is CsvBodyWriteResult.Failure) {
            return ExportWriteResult.Failure(csvWriteResult.message)
        }

        val sidecarOutcome = writeIntegritySidecarOrWarn(trips, filenamePlan)
        return ExportWriteResult.Success(
            csvFilename = filenamePlan.csvFilename,
            sidecarWritten = sidecarOutcome.sidecarWritten,
            sidecarJsonFilename = if (sidecarOutcome.sidecarWritten) filenamePlan.sidecarJsonFilename else null,
            verifyMarkdownFilename = if (sidecarOutcome.sidecarWritten) filenamePlan.verifyMarkdownFilename else null,
            integrityWarning = sidecarOutcome.warningMessage,
        )
    }

    private fun writeCsvBody(rows: List<CsvRow>, filename: String): CsvBodyWriteResult {
        return try {
            val csvContent = buildCsvContent(rows)
            writeTextFileToDownloads(filename = filename, mimeType = "text/csv", content = csvContent)
            CsvBodyWriteResult.Success(filename)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (writeFailure: Exception) {
            Timber.tag(TIMBER_TAG).e(writeFailure, "Failed to write CSV export to Downloads as %s", filename)
            CsvBodyWriteResult.Failure(writeFailure.message ?: "Export failed: ${writeFailure::class.simpleName}")
        }
    }

    /** Outcome of the (non-blocking) attempt to write the integrity sidecar + VERIFY.md. */
    private data class SidecarOutcome(val sidecarWritten: Boolean, val warningMessage: String?)

    /**
     * Attempts to write `.integrity.json` and `VERIFY.md`. Never throws and never reports
     * [ExportWriteResult.Failure] — any failure here (key read failure, generator exception, or
     * the sidecar/verify file write itself failing) degrades to [SidecarOutcome.warningMessage]
     * being set and [SidecarOutcome.sidecarWritten] = false, per the fallback-with-warning ruling.
     * A partial write (sidecar written but VERIFY.md failed, or vice versa) is also treated as a
     * full fallback — per spec, "do NOT write a partial/empty sidecar".
     */
    private fun writeIntegritySidecarOrWarn(trips: List<Trip>, filenamePlan: ExportFilenamePlan): SidecarOutcome {
        val publicKeyPem = tripSigner.getSigningPublicKeyPem()
        if (publicKeyPem == null) {
            Timber.tag(TIMBER_TAG).w(
                "writeIntegritySidecarOrWarn: signing public key unavailable — skipping sidecar " +
                    "for %s; CSV export still succeeded",
                filenamePlan.csvFilename,
            )
            return SidecarOutcome(sidecarWritten = false, warningMessage = INTEGRITY_WARNING_MESSAGE)
        }

        return try {
            val metadata = IntegritySidecarGenerator.SidecarMetadata(
                generatedAt = currentSastIsoTimestamp(),
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                csvFilename = filenamePlan.csvFilename,
                signingKeyId = TripSigner.KEYSTORE_ALIAS,
                publicKeyPem = publicKeyPem,
            )
            val sidecarJson = integritySidecarGenerator.generateSidecarJson(trips, metadata)

            writeTextFileToDownloads(
                filename = filenamePlan.sidecarJsonFilename,
                mimeType = "application/json",
                content = sidecarJson,
            )
            writeTextFileToDownloads(
                filename = filenamePlan.verifyMarkdownFilename,
                mimeType = "text/markdown",
                content = VerificationRecipeDocument.content,
            )
            SidecarOutcome(sidecarWritten = true, warningMessage = null)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (sidecarFailure: Exception) {
            Timber.tag(TIMBER_TAG).e(
                sidecarFailure,
                "writeIntegritySidecarOrWarn: FAILED for %s — CSV export still succeeded, " +
                    "falling back with warning",
                filenamePlan.csvFilename,
            )
            SidecarOutcome(sidecarWritten = false, warningMessage = INTEGRITY_WARNING_MESSAGE)
        }
    }

    /** ISO-8601 timestamp with the fixed SAST (+02:00, no DST) offset, e.g. `2026-06-30T14:22:05+02:00`. */
    private fun currentSastIsoTimestamp(): String {
        return OffsetDateTime.now(ZoneId.of(SAST_ZONE_ID)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /** Shared MediaStore Downloads write — the one I/O surface used by all three export files. */
    private fun writeTextFileToDownloads(filename: String, mimeType: String, content: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val downloadsCollection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val fileUri = requireNotNull(appContext.contentResolver.insert(downloadsCollection, contentValues)) {
            "MediaStore failed to create a Downloads entry for $filename"
        }

        appContext.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
            outputStream.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("MediaStore returned a null OutputStream for $filename")
    }

    internal fun buildCsvContent(rows: List<CsvRow>): String {
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
                    // T-007.6: SAST human-readable columns appended at end (indices 10, 11).
                    // Values are pre-formatted by CsvExportRules.buildExportRows; no formatting here.
                    row.startDateTime,
                    row.endDateTime,
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
