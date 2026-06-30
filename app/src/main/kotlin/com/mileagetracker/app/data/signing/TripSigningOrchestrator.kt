package com.mileagetracker.app.data.signing

import com.mileagetracker.app.domain.repository.SettingsRepository
import com.mileagetracker.app.domain.repository.TripRepository
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T-008 Chunk 3: orchestrates the per-trip signing flow at the `completed` transition.
 *
 * This class sits at the boundary between the data and service layers: it knows about both
 * [TripRepository] (Room) and [SettingsRepository] (DataStore tail cache), and calls [TripSigner]
 * (Android Keystore). It does NOT know about foreground-service lifecycle, GPS, or notifications —
 * those remain in [com.mileagetracker.app.service.TripTrackingForegroundService].
 *
 * Call [signAndFinalizeTrip] from the service immediately after the stop event resolves,
 * before calling [TripRepository.markTripCompleted]. The signing always completes — if the
 * Keystore fails the trip is still marked completed with null signature fields, per the T-008
 * decision ("signing failure must never block trip completion").
 *
 * Write-order / durability (per the T-008 DECISION log [2026-06-18 17:10]):
 * 1. [TripRepository.updateSigningFields] — writes signature + sequence number to Room (the
 *    durability anchor). A crash here leaves the trip unsigned but still pending; self-heal on
 *    next cold start skips it (no signature to chain from).
 * 2. [TripRepository.markTripCompleted] — flips status to COMPLETED.
 * 3. [SettingsRepository.setChainTailHash] — best-effort DataStore update. A crash here is
 *    harmless: the cold-start self-heal rebuilds the tail from Room on the next launch.
 */
@Singleton
open class TripSigningOrchestrator @Inject constructor(
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val tripSigner: TripSigner,
) {

    companion object {
        private const val TIMBER_TAG = "MT-Trip"
        private const val SHA256_ALGORITHM = "SHA-256"
    }

    /**
     * Signs [tripId] and finalizes it as COMPLETED. Safe to call from any coroutine context.
     *
     * Flow (on signing success):
     *   1. Read the current chain tail from DataStore (null for genesis).
     *   2. Count finalized trips to assign a monotonic [Trip.tripSequenceNumber].
     *   3. Load the trip from Room to populate the canonical payload.
     *   4. Call [TripSigner.signTrip] — returns [TripSigner.SigningResult].
     *   5. Write signing fields to Room via [TripRepository.updateSigningFields].
     *   6. Call [TripRepository.markTripCompleted] (flips status).
     *   7. Compute SHA-256 of the Base64 signature and advance the DataStore tail.
     *
     * Flow (on signing failure or trip not found):
     *   - [TripRepository.markTripCompleted] is still called with empty string arguments
     *     representing "no signature" — the repository writes null to both signing columns.
     *     The DataStore tail is NOT advanced (a null-signed trip cannot be a valid chain link).
     */
    open suspend fun signAndFinalizeTrip(tripId: String) {
        val tripToSign = tripRepository.getTripById(tripId)
        if (tripToSign == null) {
            Timber.tag(TIMBER_TAG).e(
                "signAndFinalizeTrip: tripId=%s not found — completing without signature",
                tripId,
            )
            finalizeWithoutSignature(tripId)
            return
        }

        val previousChainTailHash = settingsRepository.getChainTailHash()

        // Count trips whose sequence number has already been assigned (trip_sequence_number > 0),
        // excluding this trip (which has sequence 0 until now). A WORK trip currently in
        // PENDING_BUSINESS_REASON has sequence 0, so it does not count itself — fixing the
        // duplicate-sequence bug (T-034). The single-writer assumption holds for v1: only one
        // trip is finalized at a time; an atomic counter is deferred to Phase-2.
        val assignedSequenceNumber = tripRepository.countAssignedSequenceNumbers(excludeTripId = tripId) + 1

        val signingResult = tripSigner.signTrip(
            trip = tripToSign,
            previousChainTailHash = previousChainTailHash,
            tripSequenceNumber = assignedSequenceNumber,
        )

        when (signingResult) {
            is TripSigner.SigningResult.Success -> {
                // Step 1: write signing fields to Room (durability anchor).
                tripRepository.updateSigningFields(
                    tripId = tripId,
                    signatureBase64 = signingResult.signatureBase64,
                    signingKeyId = signingResult.signingKeyId,
                    tripSequenceNumber = assignedSequenceNumber,
                )
                // Step 2: flip status to COMPLETED.
                tripRepository.markTripCompleted(
                    tripId = tripId,
                    signatureBase64 = signingResult.signatureBase64,
                    signingKeyId = signingResult.signingKeyId,
                )
                // Step 3: advance the rolling tail hash (best-effort; self-heal on cold start).
                val newTailHash = computeSha256Hex(signingResult.signatureBase64)
                settingsRepository.setChainTailHash(newTailHash)

                Timber.tag(TIMBER_TAG).i(
                    "signAndFinalizeTrip: signed and completed tripId=%s sequenceNumber=%d newTail=%s",
                    tripId,
                    assignedSequenceNumber,
                    newTailHash.take(12),
                )
            }

            is TripSigner.SigningResult.Failure -> {
                // Signing failed — still complete the trip, but with null signing fields.
                // The tail is NOT advanced (a null-signed trip is not a valid chain link).
                Timber.tag(TIMBER_TAG).e(
                    signingResult.cause,
                    "signAndFinalizeTrip: signing FAILED for tripId=%s — completing with null fields",
                    tripId,
                )
                finalizeWithoutSignature(tripId)
            }
        }
    }

    /**
     * Marks the trip completed with empty signing strings, which the repository maps to nulls in
     * Room. Called when signing is not possible (Keystore failure or trip not found in Room).
     *
     * Passing empty strings rather than removing the parameter from [TripRepository.markTripCompleted]
     * keeps that interface uniform: callers always pass two strings, never an overloaded nullable
     * variant. The repository implementation writes null to the DB columns when the strings are
     * empty — see [com.mileagetracker.app.data.repository.TripRepositoryImpl.markTripCompleted].
     */
    private suspend fun finalizeWithoutSignature(tripId: String) {
        tripRepository.markTripCompleted(
            tripId = tripId,
            signatureBase64 = "",
            signingKeyId = "",
        )
    }

    /**
     * T-008 Chunk 4: cold-start self-heal. Called once from [MileageTrackerApplication.onCreate]
     * before any user interaction is possible.
     *
     * The DataStore tail hash is a derived, rebuildable cache — Room is the durability anchor.
     * This function queries the most-recently-signed Room trip and recomputes the tail from its
     * signature, overwriting whatever DataStore currently holds (which may be stale after a crash
     * between the Room write and the DataStore write, or after an app reinstall that cleared
     * DataStore but not the Room database).
     *
     * If no signed trip exists yet (first ever launch, or all trips predate signing), the DataStore
     * tail is left null — the next signing call will treat that as the genesis link.
     */
    /**
     * THREAT-MODEL LIMITATION (H-1, accepted by design): this self-heal rebuilds the chain tail from
     * whatever signed trips currently survive in Room. It therefore CANNOT detect local truncation or
     * back-dating: if the most recent signed trips are deleted from the device's database, the next
     * cold start simply rebuilds a valid-looking tail from the remaining trips and continues. The
     * hash chain proves that surviving trips were not individually edited after signing (each link's
     * prevTail still matches), but it does NOT prove that NO trips were removed from the end of the
     * chain. Detecting deletion/truncation requires an append-only external witness that records the
     * highest sequence number seen — i.e. the Phase-2 backend gap-analysis (see T-032). On-device
     * alone, end-of-chain truncation is undetectable; do not represent the local logbook as
     * truncation-proof.
     */
    open suspend fun rebuildChainTailFromRoom() {
        val mostRecentlySignedTrip = tripRepository.getMostRecentlySignedTrip()
        if (mostRecentlySignedTrip == null) {
            // No signed trips yet — leave the tail as null (genesis state).
            Timber.tag(TIMBER_TAG).i("rebuildChainTailFromRoom: no signed trips found — tail remains null")
            return
        }

        val signatureBase64 = mostRecentlySignedTrip.signatureBase64
        if (signatureBase64.isNullOrEmpty()) {
            Timber.tag(TIMBER_TAG).w(
                "rebuildChainTailFromRoom: getMostRecentlySignedTrip returned trip with null/empty signature tripId=%s",
                mostRecentlySignedTrip.id,
            )
            return
        }

        val recomputedTailHash = computeSha256Hex(signatureBase64)
        settingsRepository.setChainTailHash(recomputedTailHash)

        Timber.tag(TIMBER_TAG).i(
            "rebuildChainTailFromRoom: tail rebuilt from tripId=%s sequenceNumber=%d tail=%s",
            mostRecentlySignedTrip.id,
            mostRecentlySignedTrip.tripSequenceNumber,
            recomputedTailHash.take(12),
        )
    }

    /**
     * Computes SHA-256 of the UTF-8 bytes of [input] and returns the result as a lowercase
     * hex string. Used to advance the rolling tail hash:
     * `newTail = SHA-256(signatureBase64)` where [input] is the Base64 signature string.
     *
     * This function is pure JVM — no Android platform dependency — and is therefore testable
     * from plain JVM unit tests.
     */
    internal fun computeSha256Hex(input: String): String {
        val digestBytes = MessageDigest.getInstance(SHA256_ALGORITHM)
            .digest(input.toByteArray(Charsets.UTF_8))
        return digestBytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
