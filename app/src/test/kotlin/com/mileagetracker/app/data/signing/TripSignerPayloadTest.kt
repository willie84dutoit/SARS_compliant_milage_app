package com.mileagetracker.app.data.signing

import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-008 Chunk 2 unit tests for [TripSigner.buildCanonicalPayload]. Exercises the pure JVM
 * serialization path only — no Android Keystore operations, no org.json.JSONObject (an Android
 * platform stub that returns null from toString() under isReturnDefaultValues=true). All
 * assertions work directly against the raw JSON string produced by [buildCanonicalPayload] using
 * Kotlin stdlib string operations.
 *
 * The tests verify:
 * 1. Field order is deterministic and matches the spec exactly (LOGS.md [2026-06-18 17:10]).
 * 2. Nullable fields produce the literal token `null` (not omitted, not the string "null").
 * 3. Decimal fields round to exactly 2 decimal places (HALF_UP).
 * 4. Enum fields serialize as lowercase strings.
 * 5. Genesis trip (null prevTail) emits `"prevTail":null`.
 * 6. Chained trip (non-null prevTail) includes the exact hash string.
 * 7. Same inputs always produce an identical payload string.
 */
class TripSignerPayloadTest {

    private val tripSigner = TripSigner()

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private fun buildCompletedTrip(
        id: String = "trip-abc-123",
        classification: TripClassification = TripClassification.PRIVATE,
        startTimestamp: Long = 1_700_000_000_000L,
        endTimestamp: Long = 1_700_003_600_000L,
        startOdometerKm: Double = 100.0,
        endOdometerKm: Double = 110.0,
        verifiedOdometerKm: Double? = 110.0,
        distanceKm: Double = 10.0,
        businessReason: String? = null,
        status: TripStatus = TripStatus.COMPLETED,
    ) = Trip(
        id = id,
        classification = classification,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        startOdometerKm = startOdometerKm,
        endOdometerKm = endOdometerKm,
        verifiedOdometerKm = verifiedOdometerKm,
        distanceKm = distanceKm,
        businessReason = businessReason,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        status = status,
        photoRetention = PhotoRetentionMode.SAVED,
        createdAt = startTimestamp,
        updatedAt = endTimestamp,
        signatureBase64 = null,
        signingKeyId = null,
        tripSequenceNumber = 0,
        isManualStart = false,
    )

    /**
     * Extracts JSON key names from a flat JSON object string in the order they appear.
     * Matches the pattern `"key":` — sufficient for our single-level payload object.
     */
    private fun extractKeyOrder(jsonString: String): List<String> {
        val keyPattern = Regex("\"(\\w+)\"\\s*:")
        return keyPattern.findAll(jsonString).map { it.groupValues[1] }.toList()
    }

    // --------------------------------------------------------------------------
    // Field presence and order
    // --------------------------------------------------------------------------

    @Test
    fun `canonical payload contains all twelve required fields`() {
        val trip = buildCompletedTrip()
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        val requiredFields = listOf(
            "id", "classification", "startTimestamp", "endTimestamp",
            "startOdometerKm", "endOdometerKm", "verifiedOdometerKm", "distanceKm",
            "businessReason", "status", "prevTail", "tripSequenceNumber",
        )
        for (fieldName in requiredFields) {
            assertTrue("field '$fieldName' must be present in payload", payloadJson.contains("\"$fieldName\""))
        }
    }

    @Test
    fun `field order in JSON string matches the T-008 spec exactly`() {
        val trip = buildCompletedTrip()
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        val actualKeyOrder = extractKeyOrder(payloadJson)
        val expectedKeyOrder = listOf(
            "id", "classification", "startTimestamp", "endTimestamp",
            "startOdometerKm", "endOdometerKm", "verifiedOdometerKm", "distanceKm",
            "businessReason", "status", "prevTail", "tripSequenceNumber",
        )
        assertEquals("field order must match the T-008 spec", expectedKeyOrder, actualKeyOrder)
    }

    // --------------------------------------------------------------------------
    // Null handling — emits the literal token `null`, not the string "null"
    // --------------------------------------------------------------------------

    @Test
    fun `null verifiedOdometerKm emits JSON null literal not the string null`() {
        val trip = buildCompletedTrip(verifiedOdometerKm = null)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        // Correct: "verifiedOdometerKm":null
        // Wrong  : "verifiedOdometerKm":"null"  or field absent
        assertTrue(
            "verifiedOdometerKm must be JSON null literal: $payloadJson",
            payloadJson.contains("\"verifiedOdometerKm\":null"),
        )
    }

    @Test
    fun `null businessReason emits JSON null literal not the string null`() {
        val trip = buildCompletedTrip(businessReason = null)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "businessReason must be JSON null literal: $payloadJson",
            payloadJson.contains("\"businessReason\":null"),
        )
    }

    @Test
    fun `genesis trip null prevTail emits JSON null literal`() {
        val trip = buildCompletedTrip()
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "prevTail must be JSON null literal for genesis trip: $payloadJson",
            payloadJson.contains("\"prevTail\":null"),
        )
    }

    // --------------------------------------------------------------------------
    // Non-null prevTail
    // --------------------------------------------------------------------------

    @Test
    fun `non-null prevTail is serialized as the exact quoted hash string`() {
        val expectedTailHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        val trip = buildCompletedTrip()
        val payloadJson = tripSigner.buildCanonicalPayload(
            trip,
            previousChainTailHash = expectedTailHash,
            tripSequenceNumber = 2,
        )

        assertTrue(
            "prevTail must equal the provided hash string: $payloadJson",
            payloadJson.contains("\"prevTail\":\"$expectedTailHash\""),
        )
    }

    // --------------------------------------------------------------------------
    // Decimal precision (2 dp HALF_UP)
    // --------------------------------------------------------------------------

    @Test
    fun `distanceKm with many decimal places is rounded to exactly two`() {
        val trip = buildCompletedTrip(distanceKm = 10.12345)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        // 10.12345 rounds HALF_UP to 10.12
        assertTrue(
            "distanceKm must round to 10.12: $payloadJson",
            payloadJson.contains("\"distanceKm\":10.12"),
        )
    }

    @Test
    fun `verifiedOdometerKm rounds to two decimal places HALF_UP`() {
        // 110.999 rounds HALF_UP to 111.00
        val trip = buildCompletedTrip(verifiedOdometerKm = 110.999)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "verifiedOdometerKm must round HALF_UP to 111.00: $payloadJson",
            payloadJson.contains("\"verifiedOdometerKm\":111.00"),
        )
    }

    @Test
    fun `startOdometerKm and endOdometerKm are serialized to two decimal places`() {
        val trip = buildCompletedTrip(startOdometerKm = 100.0, endOdometerKm = 110.0)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "startOdometerKm must be 100.00: $payloadJson",
            payloadJson.contains("\"startOdometerKm\":100.00"),
        )
        assertTrue(
            "endOdometerKm must be 110.00: $payloadJson",
            payloadJson.contains("\"endOdometerKm\":110.00"),
        )
    }

    // --------------------------------------------------------------------------
    // Enum serialization — lowercase strings
    // --------------------------------------------------------------------------

    @Test
    fun `WORK classification is serialized as the lowercase string work`() {
        val workTrip = buildCompletedTrip(
            classification = TripClassification.WORK,
            businessReason = "Client visit",
        )
        val payloadJson = tripSigner.buildCanonicalPayload(workTrip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "classification must be lowercase 'work': $payloadJson",
            payloadJson.contains("\"classification\":\"work\""),
        )
    }

    @Test
    fun `PRIVATE classification is serialized as lowercase private`() {
        val privateTrip = buildCompletedTrip(classification = TripClassification.PRIVATE)
        val payloadJson = tripSigner.buildCanonicalPayload(privateTrip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "classification must be lowercase 'private': $payloadJson",
            payloadJson.contains("\"classification\":\"private\""),
        )
    }

    @Test
    fun `COMPLETED status is serialized as the lowercase string completed`() {
        val trip = buildCompletedTrip(status = TripStatus.COMPLETED)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "status must be lowercase 'completed': $payloadJson",
            payloadJson.contains("\"status\":\"completed\""),
        )
    }

    // --------------------------------------------------------------------------
    // tripSequenceNumber
    // --------------------------------------------------------------------------

    @Test
    fun `tripSequenceNumber is serialized as the integer passed in`() {
        val trip = buildCompletedTrip()
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 42)

        assertTrue(
            "tripSequenceNumber must be 42: $payloadJson",
            payloadJson.contains("\"tripSequenceNumber\":42"),
        )
    }

    // --------------------------------------------------------------------------
    // Determinism
    // --------------------------------------------------------------------------

    @Test
    fun `same inputs produce an identical payload string on repeated calls`() {
        val trip = buildCompletedTrip(
            id = "determinism-test-trip",
            distanceKm = 7.5,
            verifiedOdometerKm = 120.0,
            businessReason = null,
        )
        val firstPayload = tripSigner.buildCanonicalPayload(
            trip,
            previousChainTailHash = "deadbeef",
            tripSequenceNumber = 3,
        )
        val secondPayload = tripSigner.buildCanonicalPayload(
            trip,
            previousChainTailHash = "deadbeef",
            tripSequenceNumber = 3,
        )
        assertEquals("payload must be deterministic across identical calls", firstPayload, secondPayload)
    }

    // --------------------------------------------------------------------------
    // String fields — verbatim content and JSON escaping
    // --------------------------------------------------------------------------

    @Test
    fun `non-null businessReason is embedded verbatim in the payload`() {
        val businessReason = "Client visit to Cape Town office"
        val trip = buildCompletedTrip(
            classification = TripClassification.WORK,
            businessReason = businessReason,
        )
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "businessReason must contain the exact string: $payloadJson",
            payloadJson.contains("\"businessReason\":\"$businessReason\""),
        )
    }

    @Test
    fun `id field value is preserved exactly in the payload`() {
        val tripId = "550e8400-e29b-41d4-a716-446655440000"
        val trip = buildCompletedTrip(id = tripId)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "id must match the trip id exactly: $payloadJson",
            payloadJson.contains("\"id\":\"$tripId\""),
        )
    }

    @Test
    fun `businessReason containing a double-quote character is JSON-escaped`() {
        val businessReasonWithQuote = "Visit \"Client\" office"
        val trip = buildCompletedTrip(
            classification = TripClassification.WORK,
            businessReason = businessReasonWithQuote,
        )
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        // The double quote inside the reason must be escaped as \"
        assertTrue(
            "embedded double-quote must be backslash-escaped: $payloadJson",
            payloadJson.contains("\\\"Client\\\""),
        )
    }

    // --------------------------------------------------------------------------
    // Timestamps stored as raw longs (epoch millis)
    // --------------------------------------------------------------------------

    @Test
    fun `startTimestamp and endTimestamp are serialized as raw long integers`() {
        val startMs = 1_700_000_000_000L
        val endMs = 1_700_003_600_000L
        val trip = buildCompletedTrip(startTimestamp = startMs, endTimestamp = endMs)
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "startTimestamp must be raw long: $payloadJson",
            payloadJson.contains("\"startTimestamp\":$startMs"),
        )
        assertTrue(
            "endTimestamp must be raw long: $payloadJson",
            payloadJson.contains("\"endTimestamp\":$endMs"),
        )
    }

    // --------------------------------------------------------------------------
    // JSON escape regression tests — N-1 control-char fix
    // --------------------------------------------------------------------------

    /**
     * KEY REGRESSION TEST: a U+0001 control character inside businessReason must be escaped
     * as the six-character sequence \u0001 in the JSON output — never passed through raw.
     * This FAILS before the jsonEscapeString fix and PASSES after.
     */
    @Test
    fun `businessReason with U+0001 control char is escaped as backslash u0001`() {
        val sohChar = '\u0001'
        val reasonWithControlChar = "Visit" + sohChar + "client"
        val trip = buildCompletedTrip(
            classification = TripClassification.WORK,
            businessReason = reasonWithControlChar,
        )
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "U+0001 must be escaped as \\u0001 in the payload: $payloadJson",
            payloadJson.contains("\"businessReason\":\"Visit\\u0001client\""),
        )
        assertFalse(
            "Raw U+0001 byte must NOT appear in the payload: $payloadJson",
            payloadJson.contains('\u0001'),
        )
    }

    /**
     * Newline, tab, and backslash inside businessReason must use the RFC 8259 short escapes
     * (\n, \t, \\) — NOT the \uXXXX form — since short escapes take priority in the spec.
     */
    @Test
    fun `businessReason newline tab and backslash use short escapes not unicode escapes`() {
        val reasonWithShortEscapable = "line1\nline2\tend\\done"
        val trip = buildCompletedTrip(
            classification = TripClassification.WORK,
            businessReason = reasonWithShortEscapable,
        )
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "Newline must be escaped as \\n: $payloadJson",
            payloadJson.contains("line1\\nline2"),
        )
        assertTrue(
            "Tab must be escaped as \\t: $payloadJson",
            payloadJson.contains("line2\\tend"),
        )
        assertTrue(
            "Backslash must be escaped as \\\\: $payloadJson",
            payloadJson.contains("end\\\\done"),
        )
        assertFalse(
            "Literal newline must not appear in payload: $payloadJson",
            payloadJson.contains('\n'),
        )
        assertFalse(
            "Literal tab must not appear in payload: $payloadJson",
            payloadJson.contains('\t'),
        )
    }

    /**
     * Double-quote and backslash use their short escapes; non-ASCII (é, U+00E9) passes through
     * as UTF-8 (not \uXXXX); BEL (U+0007) is escaped as \u0007.
     */
    @Test
    fun `businessReason with double quote backslash and unicode round-trips`() {
        val bellChar = '\u0007'
        val reasonMixed = "Q\"\\\u00e9" + bellChar
        val trip = buildCompletedTrip(
            classification = TripClassification.WORK,
            businessReason = reasonMixed,
        )
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        // Double-quote escaped as \", backslash as \\, é passes through as UTF-8, BEL as \u0007
        assertTrue(
            "Payload must contain Q\\\"\\\\\u00e9\\u0007: $payloadJson",
            payloadJson.contains("\"businessReason\":\"Q\\\"\\\\\u00e9\\u0007\""),
        )
    }

    /**
     * Backspace (U+0008) uses the short escape \b; form-feed (U+000C) uses \f.
     */
    @Test
    fun `bell and form-feed control chars are escaped`() {
        val backspaceChar = '\b'
        val formFeedChar = '\u000C'
        val reasonWithEscapable = "a" + backspaceChar + "b" + formFeedChar + "c"
        val trip = buildCompletedTrip(
            classification = TripClassification.WORK,
            businessReason = reasonWithEscapable,
        )
        val payloadJson = tripSigner.buildCanonicalPayload(trip, previousChainTailHash = null, tripSequenceNumber = 1)

        assertTrue(
            "Backspace U+0008 must appear as \\b short escape: $payloadJson",
            payloadJson.contains("a\\bb"),
        )
        assertTrue(
            "Form-feed U+000C must appear as \\f short escape: $payloadJson",
            payloadJson.contains("b\\fc"),
        )
    }
}
