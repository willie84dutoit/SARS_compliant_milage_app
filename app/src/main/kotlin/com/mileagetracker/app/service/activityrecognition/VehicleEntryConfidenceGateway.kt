package com.mileagetracker.app.service.activityrecognition

import javax.inject.Inject

/**
 * T-002.4 handoff seam (placeholder — see `team/blueprints/T-002-vehicle-detection-spec.md` §4).
 *
 * [ActivityTransitionReceiver] knows only that an `IN_VEHICLE` ENTER transition occurred — the
 * Transition API event carries no confidence value (confirmed against the public
 * `ActivityTransitionEvent` surface: only `activityType`/`transitionType`/`elapsedRealTimeNanos`).
 * Reading confidence requires a second, short-lived `requestActivityUpdates()` subscription with
 * its own 30s timer and running-maximum tracking — that is the real `ConfidenceAcquisitionWindow`
 * class (T-002.4), not yet built.
 *
 * This interface exists purely so [ActivityTransitionReceiver] has a stable, injectable call site
 * to hand off to today, without the receiver constructing a [com.mileagetracker.app.domain.statemachine.TripStartEvent]
 * itself and without depending on a class that doesn't exist yet. When T-002.4 lands:
 * - `ConfidenceAcquisitionWindowImpl` (or a small adapter around it) becomes the production
 *   binding for this interface, replacing [NoOpVehicleEntryConfidenceGateway] in
 *   `ActivityRecognitionModule`.
 * - [onVehicleEntryDetected] becomes the trigger for `ConfidenceAcquisitionWindow.startWindow(...)`.
 */
interface VehicleEntryConfidenceGateway {

    /**
     * Called exactly once per `IN_VEHICLE` ENTER transition. [enteredAtEpochMillis] is the
     * wall-clock time the receiver observed the event (not the Transition API's elapsed-realtime
     * value, which is boot-relative and not directly comparable across reboots).
     */
    fun onVehicleEntryDetected(enteredAtEpochMillis: Long)
}

