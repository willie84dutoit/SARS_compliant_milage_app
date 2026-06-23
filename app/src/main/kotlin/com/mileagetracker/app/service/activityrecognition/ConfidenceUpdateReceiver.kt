package com.mileagetracker.app.service.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Receives `ActivityRecognitionResult` broadcasts from the confidence-acquisition window's 5s
 * `requestActivityUpdates()` subscription (T-002.4, per
 * `team/blueprints/T-002-vehicle-detection-spec.md` §4).
 *
 * Deliberately a separate receiver from [ActivityTransitionReceiver] — transition events
 * (`ActivityTransitionResult`) and activity-confidence updates (`ActivityRecognitionResult`) are
 * two different intent shapes from two different Play Services APIs; mixing them in one receiver
 * invites a parsing bug. This receiver is a thin parser only: it extracts the `IN_VEHICLE`
 * confidence value and forwards it to [confidenceAcquisitionWindow]. All state (running maximum,
 * timer, terminal-event firing) lives in the window class, not here.
 */
@AndroidEntryPoint
class ConfidenceUpdateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var confidenceAcquisitionWindow: ConfidenceAcquisitionWindow

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val inVehicleConfidence = result.probableActivities
            .firstOrNull { detectedActivity -> detectedActivity.type == DetectedActivity.IN_VEHICLE }
            ?.confidence
            ?: 0

        Timber.tag("MT-ActivityRecognition").d("IN_VEHICLE confidence reading: %d", inVehicleConfidence)
        confidenceAcquisitionWindow.onConfidenceReading(inVehicleConfidence)
    }
}
