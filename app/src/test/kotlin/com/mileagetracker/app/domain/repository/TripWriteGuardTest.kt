package com.mileagetracker.app.domain.repository

import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * T-033 repository-level guard tests. Verifies that guarded content-mutating writes:
 *   1. Return [TripWriteResult.RejectedSignedRow] and leave the row unchanged when the trip is
 *      already signed (signatureBase64 non-null/non-empty).
 *   2. Return [TripWriteResult.Success] and apply the change when the trip is unsigned.
 *   3. Return [TripWriteResult.TripNotFound] when the tripId does not exist.
 *
 * Uses [FakeTripRepository] rather than in-memory Room — the DAO-level guard (AND
 * signature_base64 IS NULL) is verified by the fake's [Trip.isSigned] check, which mirrors the
 * SQL predicate semantics faithfully. Real SQL coverage lives in the instrumented test suite.
 */
class TripWriteGuardTest {

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private fun buildUnsignedTrip(tripId: String = "trip-unsigned") = Trip(
        id = tripId,
        classification = TripClassification.PRIVATE,
        startTimestamp = 1_000L,
        endTimestamp = 2_000L,
        startOdometerKm = 100.0,
        endOdometerKm = 110.0,
        verifiedOdometerKm = null,
        distanceKm = 10.0,
        businessReason = null,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        status = TripStatus.PENDING_OCR,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = 0,
        isManualStart = false,
    )

    private fun buildSignedTrip(tripId: String = "trip-signed") = buildUnsignedTrip(tripId).copy(
        status = TripStatus.COMPLETED,
        signatureBase64 = "dGVzdC1zaWduYXR1cmU=",
        signingKeyId = "mileage_tracker_signing_key_v1",
        tripSequenceNumber = 1,
    )

    // --------------------------------------------------------------------------
    // updateClassification
    // --------------------------------------------------------------------------

    @Test
    fun `updateClassification on unsigned trip returns Success and applies change`() = runTest {
        val fakeRepository = FakeTripRepository()
        val unsignedTrip = buildUnsignedTrip()
        fakeRepository.setInProgressTrip(unsignedTrip)

        val writeResult = fakeRepository.updateClassification(
            tripId = unsignedTrip.id,
            classification = TripClassification.WORK,
            businessReason = "Client visit",
        )

        assertEquals("unsigned trip write must succeed", TripWriteResult.Success, writeResult)
        val updatedTrip = fakeRepository.getTripById(unsignedTrip.id)
        assertEquals("classification must be updated", TripClassification.WORK, updatedTrip?.classification)
        assertEquals("businessReason must be updated", "Client visit", updatedTrip?.businessReason)
    }

    @Test
    fun `updateClassification on signed trip returns RejectedSignedRow and does NOT change the row`() = runTest {
        val fakeRepository = FakeTripRepository()
        val signedTrip = buildSignedTrip()
        fakeRepository.setTripHistory(listOf(signedTrip))

        val writeResult = fakeRepository.updateClassification(
            tripId = signedTrip.id,
            classification = TripClassification.WORK,
            businessReason = "Attempted edit",
        )

        assertEquals(
            "signed trip write must be rejected",
            TripWriteResult.RejectedSignedRow,
            writeResult,
        )
        val rowAfterAttempt = fakeRepository.getTripById(signedTrip.id)
        assertEquals(
            "classification must NOT have changed on a signed row",
            TripClassification.PRIVATE,
            rowAfterAttempt?.classification,
        )
        assertEquals(
            "businessReason must NOT have changed on a signed row",
            null,
            rowAfterAttempt?.businessReason,
        )
    }

    @Test
    fun `updateClassification for unknown tripId returns TripNotFound`() = runTest {
        val fakeRepository = FakeTripRepository()

        val writeResult = fakeRepository.updateClassification(
            tripId = "nonexistent-trip",
            classification = TripClassification.WORK,
            businessReason = "irrelevant",
        )

        assertEquals("unknown tripId must return TripNotFound", TripWriteResult.TripNotFound, writeResult)
    }

    // --------------------------------------------------------------------------
    // updateBusinessReason
    // --------------------------------------------------------------------------

    @Test
    fun `updateBusinessReason on signed trip returns RejectedSignedRow and does NOT change the row`() = runTest {
        val fakeRepository = FakeTripRepository()
        val signedTrip = buildSignedTrip().copy(
            classification = TripClassification.WORK,
            businessReason = "Original reason",
        )
        fakeRepository.setTripHistory(listOf(signedTrip))

        val writeResult = fakeRepository.updateBusinessReason(
            tripId = signedTrip.id,
            businessReason = "Edited reason",
        )

        assertEquals(
            "signed trip business-reason write must be rejected",
            TripWriteResult.RejectedSignedRow,
            writeResult,
        )
        val rowAfterAttempt = fakeRepository.getTripById(signedTrip.id)
        assertEquals(
            "businessReason must remain unchanged on a signed row",
            "Original reason",
            rowAfterAttempt?.businessReason,
        )
    }

    @Test
    fun `updateBusinessReason on unsigned trip returns Success and applies change`() = runTest {
        val fakeRepository = FakeTripRepository()
        val unsignedTrip = buildUnsignedTrip().copy(
            classification = TripClassification.WORK,
            businessReason = "Old reason",
        )
        fakeRepository.setInProgressTrip(unsignedTrip)

        val writeResult = fakeRepository.updateBusinessReason(
            tripId = unsignedTrip.id,
            businessReason = "New reason",
        )

        assertEquals("unsigned trip write must succeed", TripWriteResult.Success, writeResult)
        val updatedTrip = fakeRepository.getTripById(unsignedTrip.id)
        assertEquals("businessReason must be updated", "New reason", updatedTrip?.businessReason)
    }

    // --------------------------------------------------------------------------
    // updateVerifiedOdometer
    // --------------------------------------------------------------------------

    @Test
    fun `updateVerifiedOdometer on signed trip returns RejectedSignedRow and does NOT change the row`() = runTest {
        val fakeRepository = FakeTripRepository()
        val signedTrip = buildSignedTrip().copy(verifiedOdometerKm = 110.0)
        fakeRepository.setTripHistory(listOf(signedTrip))

        val writeResult = fakeRepository.updateVerifiedOdometer(
            tripId = signedTrip.id,
            verifiedOdometerKm = 999.0,
        )

        assertEquals(
            "signed trip odometer write must be rejected",
            TripWriteResult.RejectedSignedRow,
            writeResult,
        )
        val rowAfterAttempt = fakeRepository.getTripById(signedTrip.id)
        assertEquals(
            "verifiedOdometerKm must remain unchanged on a signed row",
            110.0,
            rowAfterAttempt?.verifiedOdometerKm,
        )
    }

    @Test
    fun `updateVerifiedOdometer on unsigned trip returns Success and applies change`() = runTest {
        val fakeRepository = FakeTripRepository()
        val unsignedTrip = buildUnsignedTrip()
        fakeRepository.setInProgressTrip(unsignedTrip)

        val writeResult = fakeRepository.updateVerifiedOdometer(
            tripId = unsignedTrip.id,
            verifiedOdometerKm = 115.5,
        )

        assertEquals("unsigned trip odometer write must succeed", TripWriteResult.Success, writeResult)
        val updatedTrip = fakeRepository.getTripById(unsignedTrip.id)
        assertEquals("verifiedOdometerKm must be updated", 115.5, updatedTrip?.verifiedOdometerKm)
    }

    // --------------------------------------------------------------------------
    // Trip.isSigned derived property
    // --------------------------------------------------------------------------

    @Test
    fun `isSigned is true when signatureBase64 is non-null and non-empty`() {
        val signedTrip = buildSignedTrip()
        assertEquals("signed trip must have isSigned=true", true, signedTrip.isSigned)
    }

    @Test
    fun `isSigned is false when signatureBase64 is null`() {
        val unsignedTrip = buildUnsignedTrip()
        assertEquals("unsigned trip must have isSigned=false", false, unsignedTrip.isSigned)
    }

    @Test
    fun `isSigned is false when signatureBase64 is empty string`() {
        val unsignedWithEmptySig = buildUnsignedTrip().copy(signatureBase64 = "")
        assertEquals("empty-sig trip must have isSigned=false", false, unsignedWithEmptySig.isSigned)
    }
}
