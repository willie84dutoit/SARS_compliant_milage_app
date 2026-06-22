package com.mileagetracker.app.domain.statemachine

/**
 * Transient pre-trip events (T-001 blueprint §4). None of these are ever written to
 * `Trip.status` — they exist purely to drive [TripLifecycleStateMachine] through the silent-retry
 * window before a trip becomes persisted-`active`.
 */
sealed interface TripStartEvent {

    /** `IN_VEHICLE` transition fired with confidence >= the locked 70% start threshold. */
    data class ConfidentVehicleEntry(val confidencePercent: Int, val detectedAtEpochMillis: Long) : TripStartEvent

    /**
     * The locked 30-second silent retry window elapsed without confidence reaching 70%.
     * Per brief §5.9, the user is prompted regardless once this fires — [forcedLowConfidence]
     * is carried through to the notification builder for telemetry only; it never blocks the
     * prompt from appearing.
     */
    data class LowConfidenceRetryExhausted(val lastObservedConfidencePercent: Int, val forcedLowConfidence: Boolean = true) :
        TripStartEvent

    /** User-initiated manual trip start (fallback path when ActivityRecognition is unavailable). */
    data class ManualStart(val startedAtEpochMillis: Long) : TripStartEvent
}
