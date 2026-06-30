package com.mileagetracker.app.domain.model

/**
 * T-033: derived integrity status for a [Trip]. Computed from local field presence only —
 * no live ECDSA verification is performed on-device (that is a Phase-2 backend concern).
 *
 * [SIGNED]        — signature_base64 is non-null and non-empty; the trip was processed by
 *                   [com.mileagetracker.app.data.signing.TripSigningOrchestrator] and a
 *                   signature was written. Does NOT mean the signature has been cryptographically
 *                   verified against the public key.
 * [UNSIGNED]      — the trip reached COMPLETED status but signature_base64 is null/empty,
 *                   meaning the Keystore signing step failed (see the "signing failure must
 *                   never block trip completion" rule in T-008). These trips should be flagged
 *                   in the UI and export layer.
 * [NOT_FINALIZED] — the trip has not yet reached a terminal status; signing has not been
 *                   attempted.
 */
enum class TripIntegrityStatus {
    SIGNED,
    UNSIGNED,
    NOT_FINALIZED,
}
