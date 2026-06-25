package com.mileagetracker.app.data.signing

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.mileagetracker.app.domain.model.Trip
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T-008: generates an ECDSA P-256 signing key in the Android Keystore (StrongBox preferred,
 * TEE fallback) and signs the canonical per-trip payload defined in the T-008 DECISION log entry
 * [2026-06-18 17:10]. This class performs real Keystore operations and therefore cannot be
 * exercised in plain JVM unit tests — canonical payload serialization is tested separately in
 * [TripSignerPayloadTest].
 *
 * [java.util.Base64] is used instead of [android.util.Base64] so that the payload-serialization
 * path in [buildCanonicalPayload] is a pure JVM function (no Android stub dependency). The
 * project minSdk is 29 (Android 10), so [java.util.Base64] is always available at runtime.
 *
 * Per the T-008 decision, signing failure must never block trip completion; [signTrip] returns
 * [SigningResult.Failure] on any Keystore error and the caller (TripSigningOrchestrator) proceeds
 * with null signature fields rather than blocking the trip.
 */
@Singleton
class TripSigner @Inject constructor() {

    companion object {
        /**
         * Keystore alias shared with the Phase-2 backend key registry
         * (`device_keys/{uid}/{keyId}` path in Firestore, per the cost-architect ruling in the
         * T-008 DECISION log). The alias itself is the value stored in [Trip.signingKeyId].
         */
        const val KEYSTORE_ALIAS = "mileage_tracker_signing_key_v1"

        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val EC_ALGORITHM = "EC"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val P256_CURVE_NAME = "secp256r1"
        private const val TIMBER_TAG = "MT-Trip"
    }

    /**
     * Result of a signing attempt. The caller in [TripSigningOrchestrator] branches on this to
     * decide whether to write real signing fields or nulls.
     */
    sealed interface SigningResult {
        data class Success(val signatureBase64: String, val signingKeyId: String) : SigningResult
        data class Failure(val cause: Throwable) : SigningResult
    }

    /**
     * Signs [trip] against the canonical payload. [previousChainTailHash] is folded into the
     * payload as `prevTail` (null for the genesis trip). [tripSequenceNumber] is the finalization
     * counter assigned by [TripSigningOrchestrator] before this call.
     *
     * Never throws — Keystore errors are caught and returned as [SigningResult.Failure] so the
     * caller can proceed with null signing fields rather than blocking trip completion.
     */
    fun signTrip(
        trip: Trip,
        previousChainTailHash: String?,
        tripSequenceNumber: Int,
    ): SigningResult {
        return try {
            ensureKeyExists()
            val canonicalPayloadBytes =
                buildCanonicalPayload(trip, previousChainTailHash, tripSequenceNumber)
                    .toByteArray(Charsets.UTF_8)

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null)
                ?: throw IllegalStateException("Key $KEYSTORE_ALIAS not found after ensureKeyExists()")

            val signatureEngine = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureEngine.initSign(privateKey as java.security.PrivateKey)
            signatureEngine.update(canonicalPayloadBytes)
            val rawSignatureBytes = signatureEngine.sign()
            val signatureBase64 = Base64.getEncoder().withoutPadding().encodeToString(rawSignatureBytes)

            Timber.tag(TIMBER_TAG).i(
                "signTrip: success tripId=%s keyId=%s sequenceNumber=%d",
                trip.id,
                KEYSTORE_ALIAS,
                tripSequenceNumber,
            )
            SigningResult.Success(signatureBase64 = signatureBase64, signingKeyId = KEYSTORE_ALIAS)
        } catch (signingException: Exception) {
            Timber.tag(TIMBER_TAG).e(
                signingException,
                "signTrip: FAILED tripId=%s — trip will be completed with null signature fields",
                trip.id,
            )
            SigningResult.Failure(cause = signingException)
        }
    }

    /**
     * Generates the ECDSA P-256 key if it does not already exist in the AndroidKeyStore.
     * StrongBox is attempted first (API 28+); if the hardware is unavailable a TEE-backed key is
     * generated instead. Both outcomes are logged under the MT-Trip tag.
     *
     * Separated from [signTrip] so it can be called independently during cold-start self-heal
     * without performing an actual signing operation.
     */
    fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) return

        val keySpecBuilder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(P256_CURVE_NAME))
            .setDigests(KeyProperties.DIGEST_SHA256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            keySpecBuilder.setIsStrongBoxBacked(true)
            val strongBoxAttemptSucceeded = tryGenerateKey(keySpecBuilder.build())
            if (strongBoxAttemptSucceeded) {
                Timber.tag(TIMBER_TAG).i("ensureKeyExists: key generated in StrongBox alias=%s", KEYSTORE_ALIAS)
                return
            }
            // StrongBoxUnavailableException was thrown — fall through to TEE-backed key.
            keySpecBuilder.setIsStrongBoxBacked(false)
        }

        generateKeyWithSpec(keySpecBuilder.build())
        Timber.tag(TIMBER_TAG).i("ensureKeyExists: key generated in TEE alias=%s", KEYSTORE_ALIAS)
    }

    /**
     * Attempts to generate a key with the given [spec]. Returns true on success, false if
     * [StrongBoxUnavailableException] is thrown (StrongBox requested but not available on this
     * device). Any other exception propagates to the caller.
     */
    private fun tryGenerateKey(spec: KeyGenParameterSpec): Boolean {
        return try {
            generateKeyWithSpec(spec)
            true
        } catch (strongBoxUnavailableException: StrongBoxUnavailableException) {
            Timber.tag(TIMBER_TAG).w(
                strongBoxUnavailableException,
                "ensureKeyExists: StrongBox unavailable — falling back to TEE alias=%s",
                KEYSTORE_ALIAS,
            )
            false
        }
    }

    private fun generateKeyWithSpec(spec: KeyGenParameterSpec) {
        val keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM, ANDROID_KEYSTORE_PROVIDER)
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Builds the canonical UTF-8 JSON payload for signing, with fields in the EXACT order
     * mandated by the T-008 DECISION log entry [2026-06-18 17:10]:
     *
     *   id, classification, startTimestamp, endTimestamp, startOdometerKm, endOdometerKm,
     *   verifiedOdometerKm, distanceKm, businessReason, status, prevTail, tripSequenceNumber
     *
     * Implementation deliberately avoids [org.json.JSONObject] — that class is an Android platform
     * stub in JVM unit tests and returns null from toString(), making the payload untestable on the
     * plain JVM. Instead the JSON is assembled manually using a [StringBuilder] with explicit
     * field ordering. This also guarantees deterministic field order regardless of JVM version.
     *
     * Serialization rules (matching the spec exactly):
     * - Decimal fields (verifiedOdometerKm, distanceKm, startOdometerKm, endOdometerKm): 2 dp via
     *   [BigDecimal.setScale] HALF_UP, emitted as a JSON number.
     * - Null fields: emitted as the literal token `null` (not omitted, not the string "null").
     * - Enum fields: lowercase [Enum.name] string.
     * - Timestamps: raw epoch-millis [Long], emitted as a JSON number.
     * - String fields: JSON-escaped via [jsonEscapeString].
     *
     * This function is `internal` (not private) so [TripSignerPayloadTest] can call it directly
     * without going through the full Keystore path.
     */
    internal fun buildCanonicalPayload(
        trip: Trip,
        previousChainTailHash: String?,
        tripSequenceNumber: Int,
    ): String {
        val verifiedOdometerKmScaled: String = if (trip.verifiedOdometerKm != null) {
            BigDecimal(trip.verifiedOdometerKm).setScale(2, RoundingMode.HALF_UP).toPlainString()
        } else {
            "null"
        }

        val businessReasonValue: String = if (trip.businessReason != null) {
            "\"${jsonEscapeString(trip.businessReason)}\""
        } else {
            "null"
        }

        val prevTailValue: String = if (previousChainTailHash != null) {
            "\"${jsonEscapeString(previousChainTailHash)}\""
        } else {
            "null"
        }

        val startOdometerScaled =
            BigDecimal(trip.startOdometerKm).setScale(2, RoundingMode.HALF_UP).toPlainString()
        val endOdometerScaled =
            BigDecimal(trip.endOdometerKm).setScale(2, RoundingMode.HALF_UP).toPlainString()
        val distanceKmScaled =
            BigDecimal(trip.distanceKm).setScale(2, RoundingMode.HALF_UP).toPlainString()

        return buildString {
            append('{')
            append("\"id\":\"${jsonEscapeString(trip.id)}\"")
            append(",\"classification\":\"${trip.classification.name.lowercase()}\"")
            append(",\"startTimestamp\":${trip.startTimestamp}")
            append(",\"endTimestamp\":${trip.endTimestamp}")
            append(",\"startOdometerKm\":$startOdometerScaled")
            append(",\"endOdometerKm\":$endOdometerScaled")
            append(",\"verifiedOdometerKm\":$verifiedOdometerKmScaled")
            append(",\"distanceKm\":$distanceKmScaled")
            append(",\"businessReason\":$businessReasonValue")
            append(",\"status\":\"${trip.status.name.lowercase()}\"")
            append(",\"prevTail\":$prevTailValue")
            append(",\"tripSequenceNumber\":$tripSequenceNumber")
            append('}')
        }
    }

    /**
     * Escapes a string value for embedding inside a JSON double-quoted string. Handles the
     * characters that MUST be escaped per RFC 8259 §7: `"`, `\`, and the control characters
     * U+0000–U+001F. All other characters are left as-is (UTF-8 passthrough).
     */
    private fun jsonEscapeString(input: String): String = buildString(input.length + 4) {
        for (character in input) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in ' '..'' -> append("\\u${character.code.toString(16).padStart(4, '0')}")
                else -> append(character)
            }
        }
    }
}
