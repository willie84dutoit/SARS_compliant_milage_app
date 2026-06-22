package com.mileagetracker.app.domain.export

import com.mileagetracker.app.domain.classification.ClassificationRules
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripStatus

/**
 * Compliance-relevant export filter (brief §5.6/§5.9, SARS logbook integrity per the project's
 * broader spec). Written and unit-tested personally per the T-001 blueprint §6 delegation split
 * — this is short but high-consequence: a bug here either leaks an incomplete Work trip's blank
 * business reason into the exported logbook, or silently drops a trip that should have exported.
 *
 * Hard rules enforced here, both from the brief and the locked v1 facts:
 * - Completed trips only ([TripStatus.COMPLETED]) — pending/active trips are excluded.
 * - A Work trip with a blank business reason must never reach the CSV, even if its status were
 *   ever (incorrectly) COMPLETED — this is a defensive second check, not a duplicate of the state
 *   machine's gate; the export path must not trust upstream status alone for a compliance file.
 */
object CsvExportRules {

    /**
     * Builds the exact set of [CsvRow]s to write, applying both the status filter and the
     * defensive blank-business-reason guard. Order of [trips] is preserved (callers should pass
     * them already sorted, e.g. by startTimestamp descending, from the repository query).
     */
    fun buildExportRows(trips: List<Trip>): List<CsvRow> {
        return trips
            .filter { it.status == TripStatus.COMPLETED }
            .filter { ClassificationRules.isBusinessReasonSatisfied(it.classification, it.businessReason) }
            .map { trip ->
                CsvRow(
                    tripId = trip.id,
                    classification = trip.classification.name.lowercase(),
                    startTimestamp = trip.startTimestamp,
                    endTimestamp = trip.endTimestamp,
                    startOdometerKm = trip.startOdometerKm,
                    endOdometerKm = trip.endOdometerKm,
                    verifiedOdometerKm = trip.verifiedOdometerKm,
                    distanceKm = trip.distanceKm,
                    businessReason = trip.businessReason,
                    status = trip.status.name.lowercase(),
                )
            }
    }
}
