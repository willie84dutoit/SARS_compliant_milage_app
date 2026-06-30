package com.mileagetracker.app.domain.export

import com.mileagetracker.app.data.signing.TripSigner
import com.mileagetracker.app.domain.model.Trip
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T-032 Half A / Pass 1: pure, Android-free, no-I/O generator for the on-device integrity
 * sidecar JSON that accompanies a CSV export. Takes already-fetched completed trips plus a small
 * metadata holder and returns the sidecar JSON string. Performs NO Keystore calls, NO file
 * writes, and NO MediaStore access — Pass 2 (file I/O / Keystore / CsvFileWriter / ExportViewModel
 * wiring) is intentionally out of scope here, per the T-032 spec.
 *
 * Reuses [TripSigner.buildCanonicalPayload] directly (rather than re-implementing the
 * serializer) so the `canonicalPayload` string embedded in the sidecar is guaranteed
 * byte-for-byte identical to what was actually signed at trip-completion time — any drift here
 * would silently break verifiability without ever failing a build.
 *
 * Schema is fixed by the security-crypto spec (verbatim, do not change without re-checking
 * Phase-2 backend compatibility):
 * - Top level: sidecarSchemaVersion, generatedAt, appVersionName, appVersionCode, csvFilename,
 *   signatureAlgorithm, signatureEncoding, curve, hashChainAlgorithm, chainTailRule,
 *   signingKeyId, publicKeyPem, tripOrdering, signedTrips[], unsignedTrips[], limitations[].
 * - `signedTrips` ordered ASC by [Trip.tripSequenceNumber] (chain order); each entry carries
 *   tripId, tripSequenceNumber, prevTail (string|null), signatureBase64, canonicalPayload,
 *   computedTail.
 * - `computedTail(n)` = lowercase-hex SHA-256 of the UTF-8 bytes of `signatureBase64(n)`;
 *   `prevTail(n)` = `computedTail(n-1)`; genesis (lowest sequence number) `prevTail` = null.
 * - `unsignedTrips` carries trips with a null/blank signature — never dropped, never part of the
 *   chain walk.
 */
@Singleton
class IntegritySidecarGenerator @Inject constructor(
    private val tripSigner: TripSigner,
) {

    companion object {
        private const val SIDECAR_SCHEMA_VERSION = "1.0"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val SIGNATURE_ENCODING = "DER"
        private const val CURVE_NAME = "P-256"
        private const val HASH_CHAIN_ALGORITHM = "SHA-256"
        private const val CHAIN_TAIL_RULE =
            "tail(n)=SHA-256(utf8(signatureBase64(n)))_lowercase_hex; prevTail(n)=tail(n-1); genesis prevTail=null"
        private const val TRIP_ORDERING =
            "signedTrips ordered by tripSequenceNumber ascending (chain order)"
        private const val UNSIGNED_TRIP_NOTE =
            "exported but unsigned — signing unavailable at completion (Keystore failure); " +
                "not part of the integrity chain and not verifiable"

        private const val LIMITATION_KEY_LIFECYCLE =
            "Signing keys are hardware-backed and non-exportable; they are destroyed if the " +
                "app is uninstalled or its data is cleared. Trips signed before such an event " +
                "cannot be verified against the public key in this file (the key shown is the " +
                "current device key). A future server-side key registry removes this limitation."
        private const val LIMITATION_TRUNCATION =
            "This file proves that each listed signed trip was not individually altered after " +
                "signing, and that the listed trips form an unbroken hash chain. It does NOT " +
                "prove that no trips were deleted from the end of the chain on the device. " +
                "Detecting end-of-chain truncation requires the server-side append-only record " +
                "(Phase 2)."

        private const val SHA_256_ALGORITHM = "SHA-256"
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }

    /**
     * Export-time metadata not derivable from the trip list itself. [generatedAt] is injectable
     * (rather than read from the system clock inside this class) so tests can assert on an exact
     * value and so the pure-function contract holds (same inputs -> same output).
     */
    data class SidecarMetadata(
        val generatedAt: String,
        val appVersionName: String,
        val appVersionCode: Int,
        val csvFilename: String,
        val signingKeyId: String,
        val publicKeyPem: String,
    )

    /**
     * Builds the integrity sidecar JSON string for [trips] using [metadata]. Throws
     * [IllegalStateException] if any signed trip's [Trip.signingKeyId] does not match
     * [SidecarMetadata.signingKeyId] (key-rotation guard — Pass 2 surfaces this to the caller
     * rather than silently emitting a sidecar that mixes keys).
     */
    fun generateSidecarJson(trips: List<Trip>, metadata: SidecarMetadata): String {
        val signedTripsInput = trips.filter { !it.signatureBase64.isNullOrBlank() }
        val unsignedTripsInput = trips.filter { it.signatureBase64.isNullOrBlank() }

        val signedTripsSorted = signedTripsInput.sortedBy { it.tripSequenceNumber }
        val signedTripEntries = buildSignedTripEntries(signedTripsSorted, metadata.signingKeyId)
        val unsignedTripEntries = unsignedTripsInput
            .sortedBy { it.tripSequenceNumber }
            .map { trip -> UnsignedTripEntry(tripId = trip.id, tripSequenceNumber = trip.tripSequenceNumber) }

        return renderSidecarJson(metadata, signedTripEntries, unsignedTripEntries)
    }

    /**
     * Internal representation of one resolved `signedTrips[]` entry, after the chain has been
     * walked and [TripSigner.buildCanonicalPayload] has been called with the reconstructed
     * `prevTail`.
     */
    private data class SignedTripEntry(
        val tripId: String,
        val tripSequenceNumber: Int,
        val prevTail: String?,
        val signatureBase64: String,
        val canonicalPayload: String,
        val computedTail: String,
    )

    private data class UnsignedTripEntry(
        val tripId: String,
        val tripSequenceNumber: Int,
    )

    /**
     * Walks [signedTripsSorted] (already ASC by tripSequenceNumber) reconstructing the hash
     * chain and deriving each trip's canonical payload via [TripSigner.buildCanonicalPayload]
     * directly, so the embedded payload is guaranteed identical to what was actually signed.
     */
    private fun buildSignedTripEntries(
        signedTripsSorted: List<Trip>,
        expectedSigningKeyId: String,
    ): List<SignedTripEntry> {
        val entries = mutableListOf<SignedTripEntry>()
        var previousComputedTail: String? = null

        for (trip in signedTripsSorted) {
            check(trip.signingKeyId == expectedSigningKeyId) {
                "Trip ${trip.id} was signed with key '${trip.signingKeyId}' but the sidecar " +
                    "metadata signingKeyId is '$expectedSigningKeyId' — key rotation mismatch; " +
                    "refusing to generate a sidecar that mixes signing keys."
            }
            val signatureBase64 = requireNotNull(trip.signatureBase64) {
                "Trip ${trip.id} reached buildSignedTripEntries without a signatureBase64 " +
                    "(filtering bug — should have been routed to unsignedTrips)."
            }

            val canonicalPayload = tripSigner.buildCanonicalPayload(
                trip = trip,
                previousChainTailHash = previousComputedTail,
                tripSequenceNumber = trip.tripSequenceNumber,
            )
            val computedTail = sha256LowercaseHex(signatureBase64)

            entries += SignedTripEntry(
                tripId = trip.id,
                tripSequenceNumber = trip.tripSequenceNumber,
                prevTail = previousComputedTail,
                signatureBase64 = signatureBase64,
                canonicalPayload = canonicalPayload,
                computedTail = computedTail,
            )
            previousComputedTail = computedTail
        }
        return entries
    }

    /** SHA-256 of the UTF-8 bytes of [input], rendered as lowercase hex — the `tail()` rule. */
    private fun sha256LowercaseHex(input: String): String {
        val digestBytes = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(input.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(digestBytes.size * 2)
        for (byteIndex in digestBytes.indices) {
            val byteValue = digestBytes[byteIndex].toInt() and 0xFF
            hexChars[byteIndex * 2] = HEX_DIGITS[byteValue ushr 4]
            hexChars[byteIndex * 2 + 1] = HEX_DIGITS[byteValue and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Renders the full sidecar JSON, pretty-printed with 2-space indentation. Hand-rolled (no
     * JSON library is on the classpath for pure-JVM domain code — see [TripSigner]'s own
     * rationale for avoiding `org.json`, which is an Android stub under unit tests). Every string
     * value, including [SignedTripEntry.canonicalPayload], is escaped via [jsonEscapeString];
     * the canonical payload's bytes are never altered, only escaped as a JSON string value.
     */
    private fun renderSidecarJson(
        metadata: SidecarMetadata,
        signedTripEntries: List<SignedTripEntry>,
        unsignedTripEntries: List<UnsignedTripEntry>,
    ): String = buildString {
        append("{\n")
        appendField(1, "sidecarSchemaVersion", SIDECAR_SCHEMA_VERSION)
        appendField(1, "generatedAt", metadata.generatedAt)
        appendField(1, "appVersionName", metadata.appVersionName)
        appendRawField(1, "appVersionCode", metadata.appVersionCode.toString())
        appendField(1, "csvFilename", metadata.csvFilename)
        appendField(1, "signatureAlgorithm", SIGNATURE_ALGORITHM)
        appendField(1, "signatureEncoding", SIGNATURE_ENCODING)
        appendField(1, "curve", CURVE_NAME)
        appendField(1, "hashChainAlgorithm", HASH_CHAIN_ALGORITHM)
        appendField(1, "chainTailRule", CHAIN_TAIL_RULE)
        appendField(1, "signingKeyId", metadata.signingKeyId)
        appendField(1, "publicKeyPem", metadata.publicKeyPem)
        appendField(1, "tripOrdering", TRIP_ORDERING)

        append("  \"signedTrips\": ")
        appendSignedTripsArray(signedTripEntries)
        append(",\n")

        append("  \"unsignedTrips\": ")
        appendUnsignedTripsArray(unsignedTripEntries)
        append(",\n")

        append("  \"limitations\": [\n")
        append("    \"${jsonEscapeString(LIMITATION_KEY_LIFECYCLE)}\",\n")
        append("    \"${jsonEscapeString(LIMITATION_TRUNCATION)}\"\n")
        append("  ]\n")
        append("}")
    }

    private fun StringBuilder.appendField(indentLevel: Int, key: String, value: String) {
        val indent = "  ".repeat(indentLevel)
        append("$indent\"${jsonEscapeString(key)}\": \"${jsonEscapeString(value)}\",\n")
    }

    private fun StringBuilder.appendRawField(indentLevel: Int, key: String, rawValue: String) {
        val indent = "  ".repeat(indentLevel)
        append("$indent\"${jsonEscapeString(key)}\": $rawValue,\n")
    }

    private fun StringBuilder.appendSignedTripsArray(entries: List<SignedTripEntry>) {
        if (entries.isEmpty()) {
            append("[]")
            return
        }
        append("[\n")
        entries.forEachIndexed { index, entry ->
            append("    {\n")
            append("      \"tripId\": \"${jsonEscapeString(entry.tripId)}\",\n")
            append("      \"tripSequenceNumber\": ${entry.tripSequenceNumber},\n")
            val prevTailJson = entry.prevTail?.let { "\"${jsonEscapeString(it)}\"" } ?: "null"
            append("      \"prevTail\": $prevTailJson,\n")
            append("      \"signatureBase64\": \"${jsonEscapeString(entry.signatureBase64)}\",\n")
            append("      \"canonicalPayload\": \"${jsonEscapeString(entry.canonicalPayload)}\",\n")
            append("      \"computedTail\": \"${jsonEscapeString(entry.computedTail)}\"\n")
            append("    }")
            append(if (index < entries.lastIndex) ",\n" else "\n")
        }
        append("  ]")
    }

    private fun StringBuilder.appendUnsignedTripsArray(entries: List<UnsignedTripEntry>) {
        if (entries.isEmpty()) {
            append("[]")
            return
        }
        append("[\n")
        entries.forEachIndexed { index, entry ->
            append("    {\n")
            append("      \"tripId\": \"${jsonEscapeString(entry.tripId)}\",\n")
            append("      \"tripSequenceNumber\": ${entry.tripSequenceNumber},\n")
            append("      \"note\": \"${jsonEscapeString(UNSIGNED_TRIP_NOTE)}\"\n")
            append("    }")
            append(if (index < entries.lastIndex) ",\n" else "\n")
        }
        append("  ]")
    }

    /**
     * Escapes [input] for embedding inside a JSON double-quoted string per RFC 8259 §7. Mirrors
     * [TripSigner]'s private escaper rules exactly (short escapes for the standard set, `\u00XX`
     * for remaining control characters, everything else passed through as-is since UTF-8 encoding
     * happens when the final string is written to bytes by the caller in Pass 2).
     */
    private fun jsonEscapeString(input: String): String = buildString(input.length + 16) {
        for (character in input) {
            when (val characterCode = character.code) {
                // Match on the integer code point, NOT on control-character literals.
                // A char literal for a control character can be silently rewritten into a raw,
                // INVISIBLE control byte by file-writing tooling; that byte then corrupts the
                // canonical payload with no visual trace and breaks signature verification
                // undetectably (this bit us twice in escaping code). Hex codes stay plain ASCII
                // that nothing can mangle. Do NOT convert these branches back to char literals.
                0x22 -> append("\\\"") // double quote
                0x5C -> append("\\\\") // backslash
                0x08 -> append("\\b") // backspace
                0x0C -> append("\\f") // form feed
                0x0A -> append("\\n") // line feed
                0x0D -> append("\\r") // carriage return
                0x09 -> append("\\t") // tab
                else ->
                    if (characterCode < 0x20) {
                        append("\\u")
                        append(characterCode.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
            }
        }
    }
}
