package com.mileagetracker.app.domain.export

import com.mileagetracker.app.domain.classification.ClassificationRules
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
 *
 * T-007.6 amendment: two SAST human-readable columns ([CsvRow.startDateTime] / [CsvRow.endDateTime])
 * are formatted here at export time. The stored Trip epoch-millis values are never modified.
 * Timezone is set explicitly to "Africa/Johannesburg" (SAST, UTC+2, no DST) so output is
 * deterministic regardless of the device's configured timezone.
 *
 * Approach: [SimpleDateFormat] + [TimeZone.getTimeZone] — consistent with the existing project
 * convention ([CsvFileWriter] and [FileLoggingTree] already use SimpleDateFormat), requires
 * no build-file changes, and is correct on all API levels (minSdk=29).
 */
object CsvExportRules {

    private const val SAST_TIMEZONE_ID = "Africa/Johannesburg"
    private const val SAST_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

    /**
     * Thread-local formatter so [buildExportRows] is safe for concurrent callers without
     * synchronization. [SimpleDateFormat] is not thread-safe, but [ThreadLocal] gives each
     * calling thread its own instance. For the current single-threaded export use-case this
     * is belt-and-suspenders, but it is the correct practice for a shared [object].
     */
    private val sastDateFormatter: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat(SAST_DATETIME_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone(SAST_TIMEZONE_ID)
        }
    }

    private fun formatEpochMillisAsSast(epochMillis: Long): String {
        return requireNotNull(sastDateFormatter.get()) {
            "ThreadLocal SimpleDateFormat must not be null"
        }.format(Date(epochMillis))
    }

    /**
     * Builds the exact set of [CsvRow]s to write, applying both the status filter and the
     * defensive blank-business-reason guard. Order of [trips] is preserved (callers should pass
     * them already sorted, e.g. by startTimestamp descending, from the repository query).
     *
     * [CsvRow.startDateTime] and [CsvRow.endDateTime] are formatted in SAST at this point.
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
                    startDateTime = formatEpochMillisAsSast(trip.startTimestamp),
                    endDateTime = formatEpochMillisAsSast(trip.endTimestamp),
                )
            }
    }
}
