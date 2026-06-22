package com.mileagetracker.app.domain.statemachine

/**
 * Stop-side events (T-001 blueprint §4). [ConfirmedInactivity] and [UnstableSignalTimeout] both
 * terminate an active trip into `pending_ocr`, but are kept as distinct types — per the brief
 * §13, false-stop detection is the highest-risk area, and separate event types preserve which
 * path fired for telemetry/debugging without conflating two different sensor failure modes.
 */
sealed interface TripStopEvent {

    /** 3 minutes (locked) of confirmed inactivity following the last movement signal. */
    data class ConfirmedInactivity(val inactiveSinceEpochMillis: Long) : TripStopEvent

    /** 2 minutes (locked) of lost/unstable ActivityRecognition signal. */
    data class UnstableSignalTimeout(val signalLostSinceEpochMillis: Long) : TripStopEvent

    /** User tapped "stop trip" manually from the Home/Status screen. */
    data class ManualStop(val stoppedAtEpochMillis: Long) : TripStopEvent
}
