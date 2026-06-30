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
 *
 * T-039 item 7: this is the sole production implementation of [TripSigningOrchestrator], bound
 * via Hilt `@Binds` in `com.mileagetracker.app.data.di.RepositoryModule`. It is intentionally
 * final — previously this class was declared `open` purely so a test could subclass and override
 * [signAndFinalizeTrip]; that hook is gone, tests now use a hand-written fake implementing the
 * interface instead.
 */
@Singleton
class TripSigningOrchestratorImpl @Inject constructor(
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val tripSigner: TripSigner,
) : TripSigningOrchestrator {

    companion object {
        private const val TIMBER_TAG = "MT-Trip"
        private const val SHA256_ALGORITHM = "SHA-256"
    }

    override suspend fun signAndFinalizeTrip(tripId: String) {
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

    override suspend fun rebuildChainTailFromRoom() {
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
     * from plain JVM unit tests. Not part of the [TripSigningOrchestrator] interface contract;
     * tests that need it construct this implementation directly.
     */
    internal fun computeSha256Hex(input: String): String {
        val digestBytes = MessageDigest.getInstance(SHA256_ALGORITHM)
            .digest(input.toByteArray(Charsets.UTF_8))
        return digestBytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
