package com.mileagetracker.app.domain.model

/**
 * The four persisted `Trip.status` values locked by the brief (§6) and the T-001 blueprint §4.
 * Transient pre-trip/in-trip states (SilentRetry, PromptPending, unstable-signal countdown) are
 * NOT part of this enum — they live only in [com.mileagetracker.app.domain.statemachine] as
 * service-layer, in-memory state and are never written to Room. See the blueprint §4 for the
 * full transition table, including the resolved `pendingOcr` "UI-blocking-wait, not a
 * data-completeness gate" semantics.
 */
enum class TripStatus {
    ACTIVE,
    PENDING_OCR,
    PENDING_BUSINESS_REASON,
    COMPLETED,
}
