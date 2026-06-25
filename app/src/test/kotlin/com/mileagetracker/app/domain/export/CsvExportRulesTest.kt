package com.mileagetracker.app.domain.export

import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportRulesTest {

    // ------------------------------------------------------------------
    // G1: exact CSV header contract (T-007.6 amended — 12 columns)
    // ------------------------------------------------------------------

    @Test
    fun `CsvRow HEADER joinToString produces the exact T-007-6 column string with SAST columns`() {
        val actualHeaderString = CsvRow.HEADER.joinToString(",")
        assertEquals(
            "tripId,classification,startTimestamp,endTimestamp,startOdometerKm," +
                "endOdometerKm,verifiedOdometerKm,distanceKm,businessReason,status," +
                "startDateTime,endDateTime",
            actualHeaderString,
        )
    }

    // ------------------------------------------------------------------
    // G1b: SAST datetime formatting — known epoch-millis -> expected string
    // NOTE: this JVM test uses the same SimpleDateFormat + Africa/Johannesburg
    // mechanism that CsvExportRules uses. Because SimpleDateFormat respects the
    // TZ argument explicitly (not the device default), the output is identical on
    // JVM and Android runtime. The authoritative assertion of the exact formatted
    // value against a real Android runtime is in CsvExportEndToEndTest (instrumented).
    // ------------------------------------------------------------------

    @Test
    fun `buildExportRows formats startTimestamp and endTimestamp as SAST yyyy-MM-dd HH mm ss`() {
        // startTimestamp = 1_000L ms = 1970-01-01 02:00:01 SAST (UTC+2)
        // endTimestamp   = 2_000L ms = 1970-01-01 02:00:02 SAST (UTC+2)
        val completedPrivateTrip = buildTrip(
            startTimestamp = 1_000L,
            endTimestamp = 2_000L,
        )
        val exportRows = CsvExportRules.buildExportRows(listOf(completedPrivateTrip))
        assertEquals(1, exportRows.size)
        val exportedRow = exportRows.first()
        assertEquals(
            "startDateTime must be 1970-01-01 02:00:01 SAST for epoch-millis 1000",
            "1970-01-01 02:00:01",
            exportedRow.startDateTime,
        )
        assertEquals(
            "endDateTime must be 1970-01-01 02:00:02 SAST for epoch-millis 2000",
            "1970-01-01 02:00:02",
            exportedRow.endDateTime,
        )
    }

    // ------------------------------------------------------------------
    // G2: PENDING_BUSINESS_REASON trips are excluded from export
    // ------------------------------------------------------------------

    @Test
    fun `excludes trips with status PENDING_BUSINESS_REASON`() {
        val pendingBusinessReasonTrip = buildTrip(
            classification = TripClassification.WORK,
            status = TripStatus.PENDING_BUSINESS_REASON,
            businessReason = null,
        )
        val exportRows = CsvExportRules.buildExportRows(listOf(pendingBusinessReasonTrip))
        assertTrue(
            "PENDING_BUSINESS_REASON trip must not appear in export rows",
            exportRows.isEmpty(),
        )
    }

    // ------------------------------------------------------------------
    // Shared trip builder
    // ------------------------------------------------------------------

    private fun buildTrip(
        id: String = "trip-1",
        classification: TripClassification = TripClassification.PRIVATE,
        status: TripStatus = TripStatus.COMPLETED,
        businessReason: String? = null,
        startTimestamp: Long = 1_000L,
        endTimestamp: Long = 2_000L,
    ) = Trip(
        id = id,
        classification = classification,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        startOdometerKm = 100.0,
        endOdometerKm = 110.0,
        verifiedOdometerKm = 110.0,
        distanceKm = 10.0,
        businessReason = businessReason,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        status = status,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = 0,
        isManualStart = false,
    )

    @Test
    fun `excludes trips that are not completed`() {
        val activeTrip = buildTrip(status = TripStatus.ACTIVE)
        val rows = CsvExportRules.buildExportRows(listOf(activeTrip))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `excludes completed work trips with a blank business reason as a defensive guard`() {
        val workTripMissingReason = buildTrip(classification = TripClassification.WORK, businessReason = "  ")
        val rows = CsvExportRules.buildExportRows(listOf(workTripMissingReason))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `includes a completed private trip`() {
        val privateTrip = buildTrip()
        val rows = CsvExportRules.buildExportRows(listOf(privateTrip))
        assertEquals(1, rows.size)
        assertEquals("private", rows.first().classification)
    }

    @Test
    fun `includes a completed work trip with a non-blank business reason`() {
        val workTrip = buildTrip(classification = TripClassification.WORK, businessReason = "Client visit")
        val rows = CsvExportRules.buildExportRows(listOf(workTrip))
        assertEquals(1, rows.size)
        assertEquals("Client visit", rows.first().businessReason)
    }
}
