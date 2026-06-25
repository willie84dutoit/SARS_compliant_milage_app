package com.mileagetracker.app.domain.export

/**
 * The exact 12 fixed CSV columns, in order, per the amended T-007.6 contract.
 *
 * The original 10 columns (brief §5.6 / locked v1 facts) are unchanged and retain their
 * original column indices — append-at-end was deliberate to preserve every existing index.
 * Two SAST human-readable columns are appended after [status]:
 *   col 10: [startDateTime] — [startTimestamp] formatted yyyy-MM-dd HH:mm:ss in Africa/Johannesburg.
 *   col 11: [endDateTime]   — [endTimestamp]   formatted yyyy-MM-dd HH:mm:ss in Africa/Johannesburg.
 *
 * These formatted strings are populated at export time only (in [CsvExportRules.buildExportRows]).
 * The stored [Trip] and [TripEntity] values remain epoch-millis Longs — no DB schema change.
 *
 * Field order in this data class must match the header/column order exactly — CsvFileWriter
 * relies on declaration order via reflection-free, explicit serialization (T-001 build order
 * step 8), not on a map, specifically so reordering this class is a visible diff.
 */
data class CsvRow(
    val tripId: String,
    val classification: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val startOdometerKm: Double,
    val endOdometerKm: Double,
    val verifiedOdometerKm: Double?,
    val distanceKm: Double,
    val businessReason: String?,
    val status: String,
    // T-007.6: SAST human-readable columns appended at end — existing indices 0-9 are preserved.
    val startDateTime: String,
    val endDateTime: String,
) {
    companion object {
        val HEADER = listOf(
            "tripId", "classification", "startTimestamp", "endTimestamp", "startOdometerKm",
            "endOdometerKm", "verifiedOdometerKm", "distanceKm", "businessReason", "status",
            "startDateTime", "endDateTime",
        )
    }
}
