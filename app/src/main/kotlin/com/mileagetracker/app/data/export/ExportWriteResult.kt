package com.mileagetracker.app.data.export

/**
 * T-032 Half A / Pass 2: result of a [CsvWriter.writeExport] call — a result type, not an
 * exception, mirroring the original [CsvWriteResult] pattern (T-018 reference: data-layer I/O
 * boundaries return a typed result; only the boundary itself catches exceptions).
 *
 * - [Success.sidecarWritten] is true only when the integrity sidecar JSON and the VERIFY.md
 *   recipe were BOTH written successfully alongside the CSV.
 * - [Success.integrityWarning], when non-null, is the loud user-facing message for the
 *   fallback-with-warning path (Manager ruling, T-032 spec): the CSV still exports successfully,
 *   but no sidecar was written because the signing public key could not be read. The caller
 *   (ExportViewModel) surfaces this as UI state rather than silently dropping it.
 * - [Failure] is reserved for the CSV write itself failing — the export's primary, must-not-fail
 *   artifact. A sidecar failure never produces [Failure]; it always degrades to
 *   [Success] with [Success.integrityWarning] set, per the "helpful app — degrade, never block"
 *   principle.
 */
sealed interface ExportWriteResult {
    data class Success(
        val csvFilename: String,
        val sidecarWritten: Boolean,
        val sidecarJsonFilename: String?,
        val verifyMarkdownFilename: String?,
        val integrityWarning: String?,
    ) : ExportWriteResult

    data class Failure(val message: String) : ExportWriteResult
}
