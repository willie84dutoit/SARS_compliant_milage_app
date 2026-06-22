package com.mileagetracker.app.domain.export

/**
 * The exact 10 fixed CSV columns, in order, per brief §5.6 and the project's locked v1 facts.
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
) {
    companion object {
        val HEADER = listOf(
            "tripId", "classification", "startTimestamp", "endTimestamp", "startOdometerKm",
            "endOdometerKm", "verifiedOdometerKm", "distanceKm", "businessReason", "status",
        )
    }
}
