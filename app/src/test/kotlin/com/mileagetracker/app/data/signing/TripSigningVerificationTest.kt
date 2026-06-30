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
import java.security.MessageDigest

/**
 * T-008 Chunk 5 — signature-chain integrity verification tests.
 *
 * ## What is tested here (JVM unit tests)
 * Chain-link hash progression: given the [TripSigningOrchestratorImpl.computeSha256Hex] output for
 * a trip-1 signature, we verify that the same function would produce the expected `prevTailHash`
 * that trip-2's payload must contain. This is pure JVM math — no Keystore involved.
 *
 * We also verify that [TripSigningOrchestrator.rebuildChainTailFromRoom] produces the same hash
 * that [TripSigningOrchestratorImpl.computeSha256Hex] produces directly, confirming that the two
 * call sites share the same implementation.
 *
 * ## What CANNOT be tested in JVM unit tests (requires instrumented test)
 * Real ECDSA sign + verify against the Android Keystore requires a running Android Keystore
 * provider. The `AndroidKeyStore` provider is not available in the JVM test sandbox. Attempting
 * to instantiate `KeyStore.getInstance("AndroidKeyStore")` or
 * `KeyPairGenerator.getInstance("EC", "AndroidKeyStore")` in a JVM test will throw
 * `java.security.KeyStoreException: AndroidKeyStore not found`.
 *
 * The instrumented test covering the actual sign/verify round-trip lives (or should live) at:
 *
 *   `app/src/androidTest/kotlin/com/mileagetracker/app/data/signing/TripSignerInstrumentedTest.kt`
 *
 * That test must:
 *   1. Call `tripSigner.signTrip(trip, previousTail=null, sequenceNumber=1)`.
 *   2. Assert result is `SigningResult.Success`.
 *   3. Decode the Base64 signature bytes.
 *   4. Load the public key via `KeyStore.getInstance("AndroidKeyStore")`.
 *   5. Instantiate `Signature.getInstance("SHA256withECDSA")` with that key.
 *   6. Verify the raw DER signature bytes against the same canonical payload bytes.
 *   7. Assert `signature.verify(decodedBytes)` returns true.
 *   8. Assert that mutating one byte of the payload causes `verify()` to return false.
 *
 * That file is NOT created here because it requires a connected device or emulator to run, and the
 * Manager's task specification says JVM-testable portions only for Chunk 5.
 */
class TripSigningVerificationTest {

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private fun buildTrip(
        id: String = "trip-001",
        signatureBase64: String? = null,
        tripSequenceNumber: Int = 0,
    ) = Trip(
        id = id,
        classification = TripClassification.WORK,
        startTimestamp = 1_750_000_000_000L,
        endTimestamp = 1_750_003_600_000L,
        startOdometerKm = 12345.0,
        endOdometerKm = 12400.0,
        verifiedOdometerKm = 12400.0,
        distanceKm = 55.0,
        businessReason = "Client meeting downtown",
        startLatitude = -25.7461,
        startLongitude = 28.1881,
        endLatitude = -25.7600,
        endLongitude = 28.2000,
        status = TripStatus.COMPLETED,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = 1_750_000_000_000L,
        updatedAt = 1_750_003_600_000L,
        signatureBase64 = signatureBase64,
        signingKeyId = if (signatureBase64 != null) TripSigner.KEYSTORE_ALIAS else null,
        tripSequenceNumber = tripSequenceNumber,
        isManualStart = false,
    )

    private fun buildOrchestrator(
        fakeTripRepository: FakeTripRepository = FakeTripRepository(),
        fakeSettingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ) = TripSigningOrchestratorImpl(
        tripRepository = fakeTripRepository,
        settingsRepository = fakeSettingsRepository,
        tripSigner = TripSigner(),
    )

    private fun sha256Hex(input: String): String {
        val digestBytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digestBytes.joinToString("") { "%02x".format(it) }
    }

    // --------------------------------------------------------------------------
    // Chain-link hash progression (JVM-testable)
    // --------------------------------------------------------------------------

    @Test
    fun `chain tail hash for trip-1 equals SHA-256 of its signature`() {
        val signatureBase64 = "c2lnbmF0dXJlLWZvci10cmlwLTE="
        val orchestrator = buildOrchestrator()

        val chainTailHash = orchestrator.computeSha256Hex(signatureBase64)
        val expectedTailHash = sha256Hex(signatureBase64)

        assertEquals(
            "computeSha256Hex must produce the same result as raw JVM SHA-256",
            expectedTailHash,
            chainTailHash,
        )
    }

    @Test
    fun `trip-2 payload prevTail field equals SHA-256 of trip-1 signature`() {
        // This is the JVM-testable portion of chain-link verification. We simulate the
        // chain-link progression: trip-1 is signed with some signature; trip-2's payload
        // must contain prevTail = SHA-256(trip-1 signature). We verify this by directly
        // constructing the payload for trip-2 with the known prevTail and asserting that
        // the payload string contains the expected hash.
        val tripOneSignatureBase64 = "c2lnbmF0dXJlLWZvci10cmlwLTE="
        val orchestrator = buildOrchestrator()

        val tripOneTailHash = orchestrator.computeSha256Hex(tripOneSignatureBase64)
        val tripTwo = buildTrip(id = "trip-002", tripSequenceNumber = 2)
        val tripTwoCandidateSigner = TripSigner()

        val tripTwoPayload = tripTwoCandidateSigner.buildCanonicalPayload(
            trip = tripTwo,
            previousChainTailHash = tripOneTailHash,
            tripSequenceNumber = 2,
        )

        assertTrue(
            "trip-2 payload must contain prevTail equal to SHA-256 of trip-1 signature",
            tripTwoPayload.contains("\"prevTail\":\"$tripOneTailHash\""),
        )
    }

    @Test
    fun `genesis trip has null prevTail in payload`() {
        val genesisTripSigner = TripSigner()
        val genesisTrip = buildTrip(id = "trip-001", tripSequenceNumber = 1)

        val genesisPayload = genesisTripSigner.buildCanonicalPayload(
            trip = genesisTrip,
            previousChainTailHash = null,
            tripSequenceNumber = 1,
        )

        assertTrue(
            "genesis trip payload must have prevTail:null",
            genesisPayload.contains("\"prevTail\":null"),
        )
    }

    @Test
    fun `chain tail is invalidated when signature is altered`() {
        // If an attacker changes a stored signature, the derived chain tail hash will differ
        // from what the next trip recorded as its prevTail — this is detectable by an auditor.
        val originalSignatureBase64 = "b3JpZ2luYWwtc2lnbmF0dXJl"
        val alteredSignatureBase64 = "YWx0ZXJlZC1zaWduYXR1cmU="

        val orchestrator = buildOrchestrator()
        val originalTailHash = orchestrator.computeSha256Hex(originalSignatureBase64)
        val alteredTailHash = orchestrator.computeSha256Hex(alteredSignatureBase64)

        assertTrue(
            "an altered signature must produce a different chain tail hash",
            originalTailHash != alteredTailHash,
        )
    }

    @Test
    fun `computeSha256Hex output matches TripSigningOrchestratorImpl implementation directly`() {
        // Confirms that the orchestrator delegates to the same computation as the test-local
        // sha256Hex() helper — i.e. standard SHA-256, not a custom hash.
        val testSignature = "VGhpcyBpcyBhIHRlc3Qgc2lnbmF0dXJl"
        val orchestrator = buildOrchestrator()

        assertEquals(
            "orchestrator computeSha256Hex must match standard Java SHA-256",
            sha256Hex(testSignature),
            orchestrator.computeSha256Hex(testSignature),
        )
    }

    @Test
    fun `rebuildChainTailFromRoom produces same hash as computeSha256Hex for same input`() = runTest {
        val signatureBase64 = "cmVidWlsZC10ZXN0LXNpZ25hdHVyZQ=="
        val signedTrip = buildTrip(
            id = "trip-signed",
            signatureBase64 = signatureBase64,
            tripSequenceNumber = 1,
        )

        val fakeTripRepository = FakeTripRepository()
        fakeTripRepository.setTripHistory(listOf(signedTrip))

        val fakeSettingsRepository = FakeSettingsRepository()
        val orchestrator = buildOrchestrator(fakeTripRepository, fakeSettingsRepository)

        orchestrator.rebuildChainTailFromRoom()

        val expectedHash = orchestrator.computeSha256Hex(signatureBase64)
        assertEquals(
            "rebuildChainTailFromRoom must write the same hash as computeSha256Hex",
            expectedHash,
            fakeSettingsRepository.chainTailHash,
        )
    }

    // --------------------------------------------------------------------------
    // Canonical payload structural invariants (supporting verification)
    // --------------------------------------------------------------------------

    @Test
    fun `canonical payload is deterministic for identical input across two calls`() {
        val tripSigner = TripSigner()
        val trip = buildTrip()
        val previousTail = "abc123previoushashtail"

        val firstPayload = tripSigner.buildCanonicalPayload(
            trip = trip,
            previousChainTailHash = previousTail,
            tripSequenceNumber = 3,
        )
        val secondPayload = tripSigner.buildCanonicalPayload(
            trip = trip,
            previousChainTailHash = previousTail,
            tripSequenceNumber = 3,
        )

        assertEquals(
            "canonical payload must be deterministic: same inputs produce identical bytes",
            firstPayload,
            secondPayload,
        )
    }

    @Test
    fun `canonical payload changes when tripSequenceNumber changes`() {
        val tripSigner = TripSigner()
        val trip = buildTrip()

        val payloadSequenceOne = tripSigner.buildCanonicalPayload(
            trip = trip,
            previousChainTailHash = null,
            tripSequenceNumber = 1,
        )
        val payloadSequenceTwo = tripSigner.buildCanonicalPayload(
            trip = trip,
            previousChainTailHash = null,
            tripSequenceNumber = 2,
        )

        assertTrue(
            "changing tripSequenceNumber must change the canonical payload",
            payloadSequenceOne != payloadSequenceTwo,
        )
    }

    @Test
    fun `canonical payload changes when prevTailHash changes`() {
        val tripSigner = TripSigner()
        val trip = buildTrip()

        val payloadNullPrevTail = tripSigner.buildCanonicalPayload(
            trip = trip,
            previousChainTailHash = null,
            tripSequenceNumber = 1,
        )
        val payloadNonNullPrevTail = tripSigner.buildCanonicalPayload(
            trip = trip,
            previousChainTailHash = "abcdef1234567890",
            tripSequenceNumber = 1,
        )

        assertTrue(
            "changing prevTailHash must change the canonical payload",
            payloadNullPrevTail != payloadNonNullPrevTail,
        )
    }

    // --------------------------------------------------------------------------
    // Note: ECDSA sign + verify round-trip requires instrumented test
    // --------------------------------------------------------------------------
    //
    // The following test CANNOT run on the JVM. It is documented here so the
    // compliance-qa-specialist knows exactly what to implement in the instrumented suite.
    //
    // @Test
    // fun `INSTRUMENTED ONLY - signTrip and then verify with public key from Keystore`() {
    //     // 1. val result = tripSigner.signTrip(trip, previousTail = null, tripSequenceNumber = 1)
    //     // 2. assertTrue(result is TripSigner.SigningResult.Success)
    //     // 3. val signatureBytes = Base64.getDecoder().decode((result as TripSigner.SigningResult.Success).signatureBase64)
    //     // 4. val keyStore = KeyStore.getInstance("AndroidKeyStore")
    //     //    keyStore.load(null)
    //     //    val publicKey = keyStore.getCertificate(TripSigner.KEYSTORE_ALIAS).publicKey
    //     // 5. val verifier = Signature.getInstance("SHA256withECDSA")
    //     //    verifier.initVerify(publicKey)
    //     //    verifier.update(payloadBytes)
    //     // 6. assertTrue(verifier.verify(signatureBytes))
    //     // 7. Mutate one byte of payloadBytes: payloadBytes[0] = payloadBytes[0].xor(0x01.toByte())
    //     //    verifier.initVerify(publicKey); verifier.update(mutatedBytes)
    //     //    assertFalse(verifier.verify(signatureBytes))   // tamper detection
    // }
}
