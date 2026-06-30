package com.mileagetracker.app.domain.export

/**
 * T-032 Half A / Pass 2: the three filenames written by a single export, derived from ONE shared
 * stem so the CSV, integrity sidecar, and verification recipe are unambiguously bound together by
 * name alone (a SARS auditor or any third party can tell at a glance which sidecar belongs to
 * which CSV).
 *
 * The stem (`mileage_trips_YYYYMMDD_HHMMSS`) was previously computed inline inside
 * [com.mileagetracker.app.data.export.CsvFileWriter.writeToDownloads] using
 * `SimpleDateFormat("yyyyMMdd_HHmmss")` against `Date()` at call time. That inline computation is
 * now done ONCE by the caller and passed in here as [timestampStem], so all three filenames are
 * guaranteed to share the identical timestamp — computing the stem three times (once per file)
 * risked a clock tick between calls producing mismatched stems.
 *
 * [verifyMarkdownFilename] is a single static `VERIFY.md` (not stem-suffixed) per the T-032 spec
 * — the recipe itself never changes between exports, only which sidecar/CSV pair it explains, and
 * the sidecar JSON's own `csvFilename` field is what binds a given verification run to a specific
 * export.
 */
data class ExportFilenamePlan(
    val csvFilename: String,
    val sidecarJsonFilename: String,
    val verifyMarkdownFilename: String,
) {
    companion object {
        private const val VERIFY_MARKDOWN_FILENAME = "VERIFY.md"

        /** Builds the plan from an already-formatted `YYYYMMDD_HHMMSS` [timestampStem]. */
        fun fromTimestampStem(timestampStem: String): ExportFilenamePlan {
            val stem = "mileage_trips_$timestampStem"
            return ExportFilenamePlan(
                csvFilename = "$stem.csv",
                sidecarJsonFilename = "$stem.integrity.json",
                verifyMarkdownFilename = VERIFY_MARKDOWN_FILENAME,
            )
        }
    }
}
