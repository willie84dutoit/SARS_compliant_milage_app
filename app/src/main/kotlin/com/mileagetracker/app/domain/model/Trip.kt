package com.mileagetracker.app.domain.model

/**
 * Domain model for a trip — mirrors [com.mileagetracker.app.data.local.TripEntity] field-for-field
 * but carries no Room annotations, so the domain layer stays plain Kotlin/JVM per T-001 blueprint
 * §1's dependency-direction rule ("domain imports nothing Android-specific").
 *
 * Field semantics are defined in the blueprint §2; the two notes below are the ones most likely
 * to surprise a caller:
 * - [endTimestamp] equals [startTimestamp] while [status] == ACTIVE (sentinel "not yet ended",
 *   not a real end time) — see blueprint §2's resolution of the brief's non-nullable endTimestamp
 *   type against an in-progress trip.
 * - [signatureBase64] / [signingKeyId] are populated only at the literal terminal transition into
 *   COMPLETED (T-008 decision) — they are null for every other status.
 */
data class Trip(
    val id: String,
    val classification: TripClassification,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val startOdometerKm: Double,
    val endOdometerKm: Double,
    val verifiedOdometerKm: Double?,
    val distanceKm: Double,
    val businessReason: String?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val status: TripStatus,
    val photoRetention: PhotoRetentionMode,
    val createdAt: Long,
    val updatedAt: Long,
    val signatureBase64: String?,
    val signingKeyId: String?,
    /**
     * Monotonic finalization counter assigned at signing time (T-008). See [TripEntity.tripSequenceNumber]
     * for the full semantics. 0 for trips that have not yet been signed.
     */
    val tripSequenceNumber: Int,

    /**
     * H-1/H-2 fix: true when the trip was started via ManualStart (user tapped Start), false
     * when detected automatically via ActivityRecognition. Drives the Home banner label and the
     * classification notification title so manual trips are never mislabelled "Detected".
     */
    val isManualStart: Boolean,
) {
    /**
     * T-033: derived flag — true when this trip has a non-null, non-empty signature (i.e. the
     * signing fields have been written by [TripSigningOrchestrator]). No DB change; no live
     * ECDSA verification — this is a local presence check only.
     */
    val isSigned: Boolean get() = !signatureBase64.isNullOrEmpty()

    /**
     * T-033: derived integrity status for display purposes. No live ECDSA verify — presence
     * check only. A trip that is COMPLETED but unsigned (signing failure path) surfaces as
     * UNSIGNED so the UI or export layer can flag it without blocking trip completion.
     */
    val integrityStatus: TripIntegrityStatus
        get() = when {
            status == TripStatus.COMPLETED && !isSigned -> TripIntegrityStatus.UNSIGNED
            isSigned -> TripIntegrityStatus.SIGNED
            else -> TripIntegrityStatus.NOT_FINALIZED
        }
}
