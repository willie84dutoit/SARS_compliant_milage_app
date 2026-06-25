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
}
