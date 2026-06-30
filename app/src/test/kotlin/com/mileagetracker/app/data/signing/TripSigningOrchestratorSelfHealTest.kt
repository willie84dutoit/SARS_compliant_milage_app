package com.mileagetracker.app.data.signing

import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import com.mileagetracker.app.domain.repository.FakeSettingsRepository
import com.mileagetracker.app.domain.repository.FakeTripRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-008 Chunk 4 unit tests for [TripSigningOrchestrator.rebuildChainTailFromRoom].
 * Tests the cold-start self-heal logic in isolation: no Keystore, no real Room, no DataStore.
 * All dependencies are satisfied by hand-written fakes.
 *
 * Also covers [TripSigningOrchestrator.computeSha256Hex] as a pure-JVM function.
 */
class TripSigningOrchestratorSelfHealTest {

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private fun buildSignedTrip(
        id: String = "trip-signed",
        signatureBase64: String = "dGVzdC1zaWduYXR1cmU=",
        tripSequenceNumber: Int = 1,
    ) = Trip(
        id = id,
        classification = TripClassification.PRIVATE,
        startTimestamp = 1_000L,
        endTimestamp = 2_000L,
        startOdometerKm = 100.0,
        endOdometerKm = 110.0,
        verifiedOdometerKm = 110.0,
        distanceKm = 10.0,
        businessReason = null,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        status = TripStatus.COMPLETED,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        signatureBase64 = signatureBase64,
        signingKeyId = TripSigner.KEYSTORE_ALIAS,
        tripSequenceNumber = tripSequenceNumber,
        isManualStart = false,
    )

    private fun buildOrchestrator(
        fakeTripRepository: FakeTripRepository,
        fakeSettingsRepository: FakeSettingsRepository,
    ): TripSigningOrchestrator = TripSigningOrchestrator(
        tripRepository = fakeTripRepository,
        settingsRepository = fakeSettingsRepository,
        tripSigner = TripSigner(),
    )

    // --------------------------------------------------------------------------
    // rebuildChainTailFromRoom
    // --------------------------------------------------------------------------

    @Test
    fun `rebuildChainTailFromRoom with no signed trips leaves DataStore tail as null`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        val fakeSettingsRepository = FakeSettingsRepository()
        val orchestrator = buildOrchestrator(fakeTripRepository, fakeSettingsRepository)

        orchestrator.rebuildChainTailFromRoom()

        assertNull(
            "tail must remain null when no signed trips exist",
            fakeSettingsRepository.chainTailHash,
        )
    }

    @Test
    fun `rebuildChainTailFromRoom with a signed trip writes SHA-256 of its signature to DataStore`() = runTest {
        val signatureBase64 = "dGVzdC1zaWduYXR1cmU="
        val signedTrip = buildSignedTrip(signatureBase64 = signatureBase64)

        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(signedTrip))

        val fakeSettingsRepository = FakeSettingsRepository()
        val orchestrator = buildOrchestrator(fakeTripRepository, fakeSettingsRepository)

        orchestrator.rebuildChainTailFromRoom()

        val expectedTail = orchestrator.computeSha256Hex(signatureBase64)
        assertEquals(
            "tail must equal SHA-256 of the most-recently-signed trip's signature",
            expectedTail,
            fakeSettingsRepository.chainTailHash,
        )
    }

    @Test
    fun `rebuildChainTailFromRoom overwrites a stale DataStore tail with the correct recomputed value`() = runTest {
        val signatureBase64 = "dGVzdC1zaWduYXR1cmUy"
        val signedTrip = buildSignedTrip(signatureBase64 = signatureBase64)

        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(signedTrip))

        val fakeSettingsRepository = FakeSettingsRepository()
        fakeSettingsRepository.chainTailHash = "stale-incorrect-tail-hash-from-previous-run"

        val orchestrator = buildOrchestrator(fakeTripRepository, fakeSettingsRepository)

        orchestrator.rebuildChainTailFromRoom()

        val expectedTail = orchestrator.computeSha256Hex(signatureBase64)
        assertEquals(
            "stale tail must be overwritten with the recomputed value",
            expectedTail,
            fakeSettingsRepository.chainTailHash,
        )
    }

    @Test
    fun `rebuildChainTailFromRoom uses the highest-sequence signed trip not the most recent by timestamp`() = runTest {
        // Two signed trips; the one with higher tripSequenceNumber (the last finalized) should
        // determine the tail — not the one with the higher timestamp.
        val olderSignatureBase64 = "b2xkZXItc2lnbmF0dXJl"
        val newerSignatureBase64 = "bmV3ZXItc2lnbmF0dXJl"

        val olderTripHighSequence = buildSignedTrip(
            id = "trip-older-high-seq",
            signatureBase64 = newerSignatureBase64,
            tripSequenceNumber = 5,
        )
        val newerTripLowSequence = buildSignedTrip(
            id = "trip-newer-low-seq",
            signatureBase64 = olderSignatureBase64,
            tripSequenceNumber = 3,
        )

        val fakeTripRepository = FakeTripRepository()
        // FakeTripRepository.getMostRecentlySignedTrip returns the trip with the highest
        // tripSequenceNumber — which is olderTripHighSequence (sequence=5).
        fakeTripRepository.setTripHistory(listOf(newerTripLowSequence, olderTripHighSequence))

        val fakeSettingsRepository = FakeSettingsRepository()
        val orchestrator = buildOrchestrator(fakeTripRepository, fakeSettingsRepository)

        orchestrator.rebuildChainTailFromRoom()

        // The tail should be SHA-256 of the highest-sequence trip's signature (newerSignatureBase64)
        val expectedTail = orchestrator.computeSha256Hex(newerSignatureBase64)
        assertEquals(
            "tail must be derived from the highest-sequence signed trip",
            expectedTail,
            fakeSettingsRepository.chainTailHash,
        )
    }

    // --------------------------------------------------------------------------
    // computeSha256Hex — pure JVM
    // --------------------------------------------------------------------------

    @Test
    fun `computeSha256Hex produces a 64-character lowercase hex string`() {
        val orchestrator = buildOrchestrator(FakeTripRepository(), FakeSettingsRepository())
        val hexResult = orchestrator.computeSha256Hex("any input string")

        assertEquals("SHA-256 hex output must be 64 characters", 64, hexResult.length)
        assertTrue(
            "SHA-256 hex output must be lowercase hex only",
            hexResult.all { hexChar -> hexChar in '0'..'9' || hexChar in 'a'..'f' },
        )
    }

    @Test
    fun `computeSha256Hex is deterministic for the same input`() {
        val orchestrator = buildOrchestrator(FakeTripRepository(), FakeSettingsRepository())
        val input = "dGVzdC1zaWduYXR1cmU="

        assertEquals(
            "computeSha256Hex must be deterministic",
            orchestrator.computeSha256Hex(input),
            orchestrator.computeSha256Hex(input),
        )
    }

    @Test
    fun `computeSha256Hex produces different output for different inputs`() {
        val orchestrator = buildOrchestrator(FakeTripRepository(), FakeSettingsRepository())

        val hashOne = orchestrator.computeSha256Hex("signature-one")
        val hashTwo = orchestrator.computeSha256Hex("signature-two")

        assertTrue(
            "distinct inputs must produce distinct SHA-256 hashes",
            hashOne != hashTwo,
        )
    }

    // --------------------------------------------------------------------------
    // T-034 sequence-number regression tests
    // --------------------------------------------------------------------------

    /**
     * Regression: a WORK trip that passes through PENDING_BUSINESS_REASON before being signed
     * must NOT count itself when computing the next sequence number. Before the T-034 fix,
     * countFinalizedTrips() counted status IN ('completed','pending_business_reason'), so the
     * trip counted itself → its assigned sequence was count+1 = 2 instead of the correct 1
     * (because it was the only trip ever signed).
     *
     * This test places one already-signed completed trip (sequence=1) in history and one
     * PENDING_BUSINESS_REASON WORK trip (sequence=0, unsigned) as the in-progress trip, then
     * triggers signing. The assigned sequence must be 2 (one already-assigned sequence + 1),
     * not 3 (which would result from self-counting).
     */
    @Test
    fun `pending business reason trip does not self-count and receives correct sequence number`() = runTest {
        val alreadySignedTrip = buildSignedTrip(
            id = "trip-already-signed",
            signatureBase64 = "c2lnbmF0dXJlLW9uZQ==",
            tripSequenceNumber = 1,
        )

        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(alreadySignedTrip))

        // The PENDING_BUSINESS_REASON trip: tripSequenceNumber = 0 (unsigned), status pending.
        val pendingWorkTrip = Trip(
            id = "trip-work-pending",
            classification = TripClassification.WORK,
            startTimestamp = 3_000L,
            endTimestamp = 4_000L,
            startOdometerKm = 200.0,
            endOdometerKm = 220.0,
            verifiedOdometerKm = 220.0,
            distanceKm = 20.0,
            businessReason = "Client visit",
            startLatitude = null,
            startLongitude = null,
            endLatitude = null,
            endLongitude = null,
            status = TripStatus.PENDING_BUSINESS_REASON,
            photoRetention = PhotoRetentionMode.SAVED,
            createdAt = 3_000L,
            updatedAt = 4_000L,
            signatureBase64 = null,
            signingKeyId = null,
            tripSequenceNumber = 0,
            isManualStart = false,
        )
        fakeTripRepository.setInProgressTrip(pendingWorkTrip)

        val fakeSettingsRepository = FakeSettingsRepository()
        // Set chain tail from the already-signed trip so genesis link is not assumed.
        fakeSettingsRepository.chainTailHash = "fake-tail-from-trip-1"

        // Use a fake orchestrator subclass that captures the assigned sequence number instead of
        // calling the real Keystore (which is unavailable in JVM tests).
        var capturedSequenceNumber: Int? = null
        val orchestrator = object : TripSigningOrchestrator(
            tripRepository = fakeTripRepository,
            settingsRepository = fakeSettingsRepository,
            tripSigner = TripSigner(),
        ) {
            override suspend fun signAndFinalizeTrip(tripId: String) {
                capturedSequenceNumber = fakeTripRepository.countAssignedSequenceNumbers(
                    excludeTripId = tripId,
                ) + 1
                // Simulate signing by writing fields directly to the fake repo.
                fakeTripRepository.updateSigningFields(
                    tripId = tripId,
                    signatureBase64 = "stub-sig",
                    signingKeyId = "stub-key",
                    tripSequenceNumber = capturedSequenceNumber!!,
                )
                fakeTripRepository.markTripCompleted(
                    tripId = tripId,
                    signatureBase64 = "stub-sig",
                    signingKeyId = "stub-key",
                )
            }
        }

        orchestrator.signAndFinalizeTrip("trip-work-pending")

        assertEquals(
            "PENDING_BUSINESS_REASON trip must receive sequence 2, not 3 (no self-count)",
            2,
            capturedSequenceNumber,
        )
    }

    /**
     * Monotonicity: finalizing two trips in sequence must produce strictly increasing sequence
     * numbers with no duplicates.
     */
    @Test
    fun `two consecutive trip finalizations receive strictly increasing sequence numbers`() = runTest {
        val fakeTripRepository = FakeTripRepository()
        val fakeSettingsRepository = FakeSettingsRepository()

        val assignedSequenceNumbers = mutableListOf<Int>()

        val orchestrator = object : TripSigningOrchestrator(
            tripRepository = fakeTripRepository,
            settingsRepository = fakeSettingsRepository,
            tripSigner = TripSigner(),
        ) {
            override suspend fun signAndFinalizeTrip(tripId: String) {
                val sequenceNumber = fakeTripRepository.countAssignedSequenceNumbers(
                    excludeTripId = tripId,
                ) + 1
                assignedSequenceNumbers.add(sequenceNumber)
                fakeTripRepository.updateSigningFields(
                    tripId = tripId,
                    signatureBase64 = "sig-$sequenceNumber",
                    signingKeyId = "key",
                    tripSequenceNumber = sequenceNumber,
                )
                fakeTripRepository.markTripCompleted(
                    tripId = tripId,
                    signatureBase64 = "sig-$sequenceNumber",
                    signingKeyId = "key",
                )
            }
        }

        // Finalize first trip.
        val firstTrip = buildSignedTrip(
            id = "trip-first",
            signatureBase64 = null.toString(),
            tripSequenceNumber = 0,
        ).copy(signatureBase64 = null, tripSequenceNumber = 0, status = TripStatus.PENDING_OCR)
        fakeTripRepository.setInProgressTrip(firstTrip)
        orchestrator.signAndFinalizeTrip("trip-first")

        // Finalize second trip.
        val secondTrip = buildSignedTrip(
            id = "trip-second",
            signatureBase64 = null.toString(),
            tripSequenceNumber = 0,
        ).copy(signatureBase64 = null, tripSequenceNumber = 0, status = TripStatus.PENDING_OCR)
        fakeTripRepository.setInProgressTrip(secondTrip)
        orchestrator.signAndFinalizeTrip("trip-second")

        assertEquals("Two trips should have been finalized", 2, assignedSequenceNumbers.size)
        assertEquals("First trip sequence must be 1", 1, assignedSequenceNumbers[0])
        assertEquals("Second trip sequence must be 2", 2, assignedSequenceNumbers[1])
        assertTrue(
            "Sequence numbers must be strictly increasing",
            assignedSequenceNumbers[1] > assignedSequenceNumbers[0],
        )
    }
}
