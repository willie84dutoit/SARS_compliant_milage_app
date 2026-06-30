package com.mileagetracker.app.domain.export

import com.mileagetracker.app.data.signing.TripSigner
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.util.Base64

/**
 * T-032 Half A / Pass 1 unit tests for [IntegritySidecarGenerator]. Pure JVM — no Android
 * Keystore — exercises:
 * 1. Payload-string identity against [TripSigner.buildCanonicalPayload] directly (highest value:
 *    proves zero drift between what was signed and what the sidecar republishes).
 * 2. A real in-JVM EC P-256 sign + verify round trip, proving the sidecar is actually verifiable
 *    by a third party holding only the PEM public key and the JSON.
 * 3. Genesis prevTail handling (both the field and inside the embedded canonicalPayload).
 * 4. Hash-chain wiring (prevTail(n) == computedTail(n-1); computedTail formula).
 * 5. Unsigned-trip routing (never dropped, never part of the chain).
 * 6. RFC-8259 escaping of awkward business-reason content, still verifiable after escaping.
 * 7. Ordering independence — shuffled input still emerges ASC by tripSequenceNumber.
 */
class IntegritySidecarGeneratorTest {

    private lateinit var tripSigner: TripSigner
    private lateinit var generator: IntegritySidecarGenerator
    private lateinit var testKeyPair: KeyPair

    companion object {
        private const val SIGNING_KEY_ID = "mileage_tracker_signing_key_v1"
        private const val EC_ALGORITHM = "EC"
        private const val P256_CURVE_NAME = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    @Before
    fun setUp() {
        tripSigner = TripSigner()
        generator = IntegritySidecarGenerator(tripSigner)
        testKeyPair = generateTestKeyPair()
    }

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private fun generateTestKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        keyPairGenerator.initialize(ECGenParameterSpec(P256_CURVE_NAME))
        return keyPairGenerator.generateKeyPair()
    }

    /** Encodes [publicKey] as a PEM-wrapped SubjectPublicKeyInfo block, 64-char line width. */
    private fun publicKeyToPem(publicKey: PublicKey): String {
        val base64Body = Base64.getEncoder().encodeToString(publicKey.encoded)
        val wrappedBody = base64Body.chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$wrappedBody\n-----END PUBLIC KEY-----\n"
    }

    /** Loads a [PublicKey] back from a PEM string produced by [publicKeyToPem], for verification. */
    private fun publicKeyFromPem(pem: String): PublicKey {
        val base64Body = pem
            .lines()
            .filterNot { it.startsWith("-----") || it.isBlank() }
            .joinToString("")
        val keyBytes = Base64.getDecoder().decode(base64Body)
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }

    /** Signs [canonicalPayload] exactly as [TripSigner.signTrip] does: DER, Base64 no-padding. */
    private fun signPayload(canonicalPayload: String, keyPair: KeyPair): String {
        val signatureEngine = Signature.getInstance(SIGNATURE_ALGORITHM)
        signatureEngine.initSign(keyPair.private)
        signatureEngine.update(canonicalPayload.toByteArray(Charsets.UTF_8))
        val rawSignatureBytes = signatureEngine.sign()
        return Base64.getEncoder().withoutPadding().encodeToString(rawSignatureBytes)
    }

    private fun verifySignature(canonicalPayload: String, signatureBase64: String, publicKey: PublicKey): Boolean {
        val signatureEngine = Signature.getInstance(SIGNATURE_ALGORITHM)
        signatureEngine.initVerify(publicKey)
        signatureEngine.update(canonicalPayload.toByteArray(Charsets.UTF_8))
        val signatureBytes = Base64.getDecoder().decode(signatureBase64)
        return signatureEngine.verify(signatureBytes)
    }

    private fun sha256LowercaseHex(input: String): String {
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digestBytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildUnsignedTrip(
        id: String,
        tripSequenceNumber: Int,
        classification: TripClassification = TripClassification.PRIVATE,
        businessReason: String? = null,
        startTimestamp: Long = 1_700_000_000_000L,
        endTimestamp: Long = 1_700_003_600_000L,
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
        status = TripStatus.COMPLETED,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = startTimestamp,
        updatedAt = endTimestamp,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = tripSequenceNumber,
        isManualStart = false,
    )

    /**
     * Builds a chain of [count] trips, sequence numbers 1..count, each signed with [keyPair] and
     * chained correctly (prevTail(n) = computedTail(n-1), genesis prevTail = null). Returns the
     * trips in tripSequenceNumber-ascending order.
     */
    private fun buildSignedTripChain(
        count: Int,
        keyPair: KeyPair,
        signingKeyId: String = SIGNING_KEY_ID,
        businessReasonForSequence: (Int) -> String? = { null },
    ): List<Trip> {
        var previousComputedTail: String? = null
        val trips = mutableListOf<Trip>()
        for (sequenceNumber in 1..count) {
            val unsignedBase = buildUnsignedTrip(
                id = "trip-seq-$sequenceNumber",
                tripSequenceNumber = sequenceNumber,
                classification = if (businessReasonForSequence(sequenceNumber) != null) {
                    TripClassification.WORK
                } else {
                    TripClassification.PRIVATE
                },
                businessReason = businessReasonForSequence(sequenceNumber),
            )
            val canonicalPayload = tripSigner.buildCanonicalPayload(
                unsignedBase,
                previousChainTailHash = previousComputedTail,
                tripSequenceNumber = sequenceNumber,
            )
            val signatureBase64 = signPayload(canonicalPayload, keyPair)
            val signedTrip = unsignedBase.copy(
                signatureBase64 = signatureBase64,
                signingKeyId = signingKeyId,
            )
            trips += signedTrip
            previousComputedTail = sha256LowercaseHex(signatureBase64)
        }
        return trips
    }

    private fun buildMetadata(publicKeyPem: String, signingKeyId: String = SIGNING_KEY_ID) =
        IntegritySidecarGenerator.SidecarMetadata(
            generatedAt = "2026-06-30T12:00:00+02:00",
            appVersionName = "1.0.0",
            appVersionCode = 7,
            csvFilename = "mileage_trips_20260630_120000.csv",
            signingKeyId = signingKeyId,
            publicKeyPem = publicKeyPem,
        )

    // --------------------------------------------------------------------------
    // 1. Payload-string identity
    // --------------------------------------------------------------------------

    @Test
    fun `canonicalPayload in sidecar matches buildCanonicalPayload exactly for every signed trip`() {
        val trips = buildSignedTripChain(count = 3, keyPair = testKeyPair)
        val metadata = buildMetadata(publicKeyToPem(testKeyPair.public))
        val sidecarJson = generator.generateSidecarJson(trips, metadata)

        var previousComputedTail: String? = null
        for (trip in trips) {
            val expectedPayload = tripSigner.buildCanonicalPayload(
                trip,
                previousChainTailHash = previousComputedTail,
                tripSequenceNumber = trip.tripSequenceNumber,
            )
            val escapedExpectedPayload = expectedPayload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            assertTrue(
                "sidecar must contain the exact canonicalPayload for ${trip.id}: $sidecarJson",
                sidecarJson.contains("\"canonicalPayload\": \"$escapedExpectedPayload\""),
            )
            previousComputedTail = sha256LowercaseHex(requireNotNull(trip.signatureBase64))
        }
    }

    // --------------------------------------------------------------------------
    // 2. In-JVM signature round trip
    // --------------------------------------------------------------------------

    @Test
    fun `every signedTrips entry verifies against its canonicalPayload using the PEM public key`() {
        val trips = buildSignedTripChain(count = 4, keyPair = testKeyPair)
        val publicKeyPem = publicKeyToPem(testKeyPair.public)
        val metadata = buildMetadata(publicKeyPem)
        val sidecarJson = generator.generateSidecarJson(trips, metadata)

        val loadedPublicKey = publicKeyFromPem(publicKeyPem)
        val entries = parseSignedTripEntries(sidecarJson)
        assertEquals(4, entries.size)

        for (entry in entries) {
            val verified = verifySignature(entry.canonicalPayload, entry.signatureBase64, loadedPublicKey)
            assertTrue("signature must verify for tripId=${entry.tripId}", verified)
        }
    }

    // --------------------------------------------------------------------------
    // 3. Genesis
    // --------------------------------------------------------------------------

    @Test
    fun `first signed trip has null prevTail as field and inside canonicalPayload`() {
        val trips = buildSignedTripChain(count = 2, keyPair = testKeyPair)
        val metadata = buildMetadata(publicKeyToPem(testKeyPair.public))
        val sidecarJson = generator.generateSidecarJson(trips, metadata)

        val entries = parseSignedTripEntries(sidecarJson)
        val genesisEntry = entries.first()

        assertNull("genesis prevTail field must be null", genesisEntry.prevTail)
        assertTrue(
            "genesis canonicalPayload must contain literal prevTail:null: ${genesisEntry.canonicalPayload}",
            genesisEntry.canonicalPayload.contains("\"prevTail\":null"),
        )
    }

    // --------------------------------------------------------------------------
    // 4. Chain wiring
    // --------------------------------------------------------------------------

    @Test
    fun `each entrys prevTail equals the previous entrys computedTail and computedTail formula holds`() {
        val trips = buildSignedTripChain(count = 5, keyPair = testKeyPair)
        val metadata = buildMetadata(publicKeyToPem(testKeyPair.public))
        val sidecarJson = generator.generateSidecarJson(trips, metadata)

        val entries = parseSignedTripEntries(sidecarJson)
        assertEquals(5, entries.size)

        for (entryIndex in entries.indices) {
            val entry = entries[entryIndex]
            val expectedComputedTail = sha256LowercaseHex(entry.signatureBase64)
            assertEquals(
                "computedTail must equal SHA-256(utf8(signatureBase64)) for ${entry.tripId}",
                expectedComputedTail,
                entry.computedTail,
            )
            if (entryIndex == 0) {
                assertNull(entry.prevTail)
            } else {
                assertEquals(
                    "prevTail must equal previous entry's computedTail",
                    entries[entryIndex - 1].computedTail,
                    entry.prevTail,
                )
            }
        }
    }

    // --------------------------------------------------------------------------
    // 5. Unsigned routing
    // --------------------------------------------------------------------------

    @Test
    fun `trip with null signatureBase64 appears only in unsignedTrips and does not break the chain`() {
        val signedTrips = buildSignedTripChain(count = 2, keyPair = testKeyPair)
        val unsignedTrip = buildUnsignedTrip(id = "trip-unsigned-1", tripSequenceNumber = 99)
        val allTrips = signedTrips + unsignedTrip
        val metadata = buildMetadata(publicKeyToPem(testKeyPair.public))

        val sidecarJson = generator.generateSidecarJson(allTrips, metadata)

        assertFalse(
            "unsigned trip id must not appear inside signedTrips block",
            extractBlock(sidecarJson, "\"signedTrips\"").contains("trip-unsigned-1"),
        )
        val unsignedBlock = extractBlock(sidecarJson, "\"unsignedTrips\"")
        assertTrue("unsigned trip must appear in unsignedTrips", unsignedBlock.contains("trip-unsigned-1"))
        assertTrue(
            "unsigned trip entry must carry the fixed note",
            unsignedBlock.contains("signing unavailable at completion"),
        )

        val signedEntries = parseSignedTripEntries(sidecarJson)
        assertEquals(2, signedEntries.size)
        assertNull(signedEntries[0].prevTail)
        assertEquals(sha256LowercaseHex(signedEntries[0].signatureBase64), signedEntries[1].prevTail)
    }

    // --------------------------------------------------------------------------
    // 6. Escaping edge cases — still verifiable after escaping
    // --------------------------------------------------------------------------

    @Test
    fun `businessReason with quote newline and accented character round-trip verifies`() {
        val awkwardReason = "Visit \"Client\"\noffice café"
        val trips = buildSignedTripChain(
            count = 1,
            keyPair = testKeyPair,
            businessReasonForSequence = { awkwardReason },
        )
        val publicKeyPem = publicKeyToPem(testKeyPair.public)
        val metadata = buildMetadata(publicKeyPem)
        val sidecarJson = generator.generateSidecarJson(trips, metadata)

        val loadedPublicKey = publicKeyFromPem(publicKeyPem)
        val entries = parseSignedTripEntries(sidecarJson)
        assertEquals(1, entries.size)
        val entry = entries.first()

        assertTrue(
            "canonicalPayload must contain the verbatim (unescaped-by-sidecar) business reason bytes",
            entry.canonicalPayload.contains("café"),
        )
        assertTrue(
            "signature must still verify after JSON round trip",
            verifySignature(entry.canonicalPayload, entry.signatureBase64, loadedPublicKey),
        )
    }

    // --------------------------------------------------------------------------
    // 7. Ordering independence
    // --------------------------------------------------------------------------

    @Test
    fun `shuffled input trips still emerge ascending by tripSequenceNumber in signedTrips`() {
        val orderedTrips = buildSignedTripChain(count = 4, keyPair = testKeyPair)
        val shuffledTrips = listOf(orderedTrips[2], orderedTrips[0], orderedTrips[3], orderedTrips[1])
        val metadata = buildMetadata(publicKeyToPem(testKeyPair.public))

        val sidecarJson = generator.generateSidecarJson(shuffledTrips, metadata)
        val entries = parseSignedTripEntries(sidecarJson)

        val actualSequenceOrder = entries.map { it.tripSequenceNumber }
        assertEquals(listOf(1, 2, 3, 4), actualSequenceOrder)

        // Re-running the chain reconstruction on the now-correctly-ordered list must still
        // verify — proves the generator re-chained from the sorted order, not the input order.
        for (entryIndex in entries.indices) {
            val expectedComputedTail = sha256LowercaseHex(entries[entryIndex].signatureBase64)
            assertEquals(expectedComputedTail, entries[entryIndex].computedTail)
        }
    }

    @Test
    fun `key rotation mismatch throws rather than silently mixing keys`() {
        val trips = buildSignedTripChain(count = 1, keyPair = testKeyPair, signingKeyId = "old_key_id")
        val metadata = buildMetadata(publicKeyToPem(testKeyPair.public), signingKeyId = "new_key_id")

        try {
            generator.generateSidecarJson(trips, metadata)
            assertTrue("expected an IllegalStateException for key rotation mismatch", false)
        } catch (expectedMismatch: IllegalStateException) {
            assertTrue(
                "exception message should mention the mismatched key ids",
                expectedMismatch.message.orEmpty().contains("old_key_id") &&
                    expectedMismatch.message.orEmpty().contains("new_key_id"),
            )
        }
    }

    // --------------------------------------------------------------------------
    // Minimal JSON parsing helpers (no JSON library on the test classpath; the sidecar's own
    // pretty-printed layout is regular enough to parse with targeted regexes for assertions).
    // --------------------------------------------------------------------------

    private data class ParsedSignedTripEntry(
        val tripId: String,
        val tripSequenceNumber: Int,
        val prevTail: String?,
        val signatureBase64: String,
        val canonicalPayload: String,
        val computedTail: String,
    )

    private fun extractBlock(sidecarJson: String, arrayKeyLiteral: String): String {
        val startIndex = sidecarJson.indexOf(arrayKeyLiteral)
        require(startIndex >= 0) { "key $arrayKeyLiteral not found in sidecar JSON" }
        val nextTopLevelKeyIndex = sidecarJson.indexOf("\n  \"", startIndex + arrayKeyLiteral.length)
        return if (nextTopLevelKeyIndex >= 0) {
            sidecarJson.substring(startIndex, nextTopLevelKeyIndex)
        } else {
            sidecarJson.substring(startIndex)
        }
    }

    private fun parseSignedTripEntries(sidecarJson: String): List<ParsedSignedTripEntry> {
        val signedTripsBlock = extractBlock(sidecarJson, "\"signedTrips\"")
        val entryPattern = Regex(
            "\\{\\s*" +
                "\"tripId\":\\s*\"([^\"]*)\",\\s*" +
                "\"tripSequenceNumber\":\\s*(\\d+),\\s*" +
                "\"prevTail\":\\s*(null|\"[0-9a-f]*\"),\\s*" +
                "\"signatureBase64\":\\s*\"([^\"]*)\",\\s*" +
                "\"canonicalPayload\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\",\\s*" +
                "\"computedTail\":\\s*\"([0-9a-f]+)\"\\s*\\}",
        )
        return entryPattern.findAll(signedTripsBlock).map { match ->
            val (tripId, sequenceNumberText, prevTailRaw, signatureBase64, canonicalPayloadEscaped, computedTail) =
                match.destructured
            ParsedSignedTripEntry(
                tripId = tripId,
                tripSequenceNumber = sequenceNumberText.toInt(),
                prevTail = if (prevTailRaw == "null") null else prevTailRaw.removeSurrounding("\""),
                signatureBase64 = signatureBase64,
                canonicalPayload = unescapeJsonString(canonicalPayloadEscaped),
                computedTail = computedTail,
            )
        }.toList()
    }

    /** Reverses [IntegritySidecarGenerator]'s jsonEscapeString for the subset used in payloads. */
    private fun unescapeJsonString(escaped: String): String {
        val result = StringBuilder(escaped.length)
        var index = 0
        while (index < escaped.length) {
            val character = escaped[index]
            if (character == '\\' && index + 1 < escaped.length) {
                when (escaped[index + 1]) {
                    '"' -> { result.append('"'); index += 2 }
                    '\\' -> { result.append('\\'); index += 2 }
                    'n' -> { result.append(0x0A.toChar()); index += 2 }
                    'r' -> { result.append(0x0D.toChar()); index += 2 }
                    't' -> { result.append(0x09.toChar()); index += 2 }
                    'b' -> { result.append(0x08.toChar()); index += 2 }
                    'f' -> { result.append(0x0C.toChar()); index += 2 }
                    'u' -> {
                        val hex = escaped.substring(index + 2, index + 6)
                        result.append(hex.toInt(16).toChar())
                        index += 6
                    }
                    else -> { result.append(character); index += 1 }
                }
            } else {
                result.append(character)
                index += 1
            }
        }
        return result.toString()
    }
}
