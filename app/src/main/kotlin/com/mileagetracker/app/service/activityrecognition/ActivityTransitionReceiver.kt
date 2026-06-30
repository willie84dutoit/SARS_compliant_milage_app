package com.mileagetracker.app.service.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives `ActivityTransitionResult` broadcasts (T-002.3, per
 * `team/blueprints/T-002-vehicle-detection-spec.md` §3).
 *
 * This receiver does **not** start a trip directly and does **not** construct a
 * [com.mileagetracker.app.domain.statemachine.TripStartEvent] itself — the Transition API event
 * carries no confidence value (only `activityType`/`transitionType`/`elapsedRealTimeNanos`), so
 * there is nothing here to feed `TripLifecycleStateMachine.onStartEvent()` with. On `ENTER` it
 * hands off to [confidenceEntryGateway] (the T-002.4 seam — see
 * [VehicleEntryConfidenceGateway]'s doc for what plugs in there once the confidence-acquisition
 * window exists). `EXIT` is logged only — T-004 owns stop detection via
 * inactivity/unstable-signal/manual, not the Transition API's EXIT signal.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var confidenceEntryGateway: VehicleEntryConfidenceGateway

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            if (event.activityType != DetectedActivity.IN_VEHICLE) continue

            when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    Timber.tag("MT-ActivityRecognition")
                        .i("IN_VEHICLE ENTER received at %d", event.elapsedRealTimeNanos)
                    confidenceEntryGateway.onVehicleEntryDetected(enteredAtEpochMillis = System.currentTimeMillis())
                }

                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    Timber.tag("MT-ActivityRecognition")
                        .i("IN_VEHICLE EXIT received at %d", event.elapsedRealTimeNanos)
                    // No action in T-002 — EXIT is not part of the locked stop rule (T-004 owns
                    // stop detection via inactivity/unstable-signal/manual only). Logged for
                    // telemetry only.
                }
            }
        }
    }
}
