package com.mileagetracker.app.service.activityrecognition

import com.mileagetracker.app.domain.statemachine.TripStartEvent
import kotlinx.coroutines.flow.Flow

/**
 * T-002.4. The Transition API's ENTER event carries no confidence value, so this class runs a
 * second, short-lived `requestActivityUpdates()` subscription (5s interval) purely to read
 * `DetectedActivity.getConfidence()` for `IN_VEHICLE` during the 30s window after an ENTER event.
 * Tracks the RUNNING MAXIMUM confidence seen (not the latest reading) so one high reading is not
 * erased by a lower one delivered afterward. Fires exactly one terminal event — confident-entry
 * (max reached 70+) or retry-exhausted (timer elapsed first) — then unregisters itself. Must never
 * be left registered after either branch fires; a leaked subscription means full-rate activity
 * updates running indefinitely in the background, which is the single biggest battery risk in the
 * detection path (see `team/blueprints/T-002-vehicle-detection-spec.md` §4 "Battery implications").
 */
interface ConfidenceAcquisitionWindow {

    /** Starts the window following an IN_VEHICLE ENTER event. No-op if a window is already running. */
    fun startWindow(enteredAtEpochMillis: Long)

    /**
     * Forwards a single confidence reading (0-100) for `IN_VEHICLE`, sourced by
     * [ConfidenceUpdateReceiver] from a `requestActivityUpdates()` callback. Tracks the running
     * maximum for the active window; no-op if no window is currently active.
     */
    fun onConfidenceReading(confidencePercent: Int)

    /** True while a window is actively subscribed; exposed for tests/diagnostics, not for control flow. */
    fun isWindowActive(): Boolean

    /** Cancels any in-flight window without firing a terminal event (e.g. service teardown). Must unregister updates. */
    fun cancel()

    /** Emits the terminal TripStartEvent.ConfidentVehicleEntry or TripStartEvent.LowConfidenceRetryExhausted exactly once per window. */
    fun observeResults(): Flow<TripStartEvent>
}
