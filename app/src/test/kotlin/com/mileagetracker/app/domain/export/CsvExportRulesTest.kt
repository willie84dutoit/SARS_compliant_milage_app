package com.mileagetracker.app.domain.export

import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportRulesTest {

    private fun buildTrip(
        id: String = "trip-1",
        classification: TripClassification = TripClassification.PRIVATE,
        status: TripStatus = TripStatus.COMPLETED,
        businessReason: String? = null,
    ) = Trip(
        id = id,
        classification = classification,
        startTimestamp = 1_000L,
        endTimestamp = 2_000L,
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
