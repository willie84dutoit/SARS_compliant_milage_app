package com.mileagetracker.app.service.activityrecognition

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers/unregisters the ActivityRecognition transition request (T-002.3, per
 * `team/blueprints/T-002-vehicle-detection-spec.md` §3).
 *
 * Both `IN_VEHICLE` ENTER and EXIT are registered on the same [ActivityTransitionRequest], even
 * though only ENTER is currently acted on by [ActivityTransitionReceiver]. EXIT is reserved for
 * a future stop-side signal-quality use (T-004) — registering it now costs nothing extra on the
 * same request and avoids a second registration round-trip later. Do not remove the EXIT entry
 * as "dead code" without re-reading the spec's §7 open-flags note.
 *
 * Idempotency requirement (blueprint §2 step 5): registering the same PendingIntent twice (e.g.
 * service restarted while a trip was already active) must not create a second trip. That
 * guarantee comes from `getInProgressTrip()` being checked on every `onStartCommand` — NOT from
 * this class refusing to re-register. Re-registering the same request with the same
 * PendingIntent request code is intentionally a safe no-op per the Play Services API contract.
 */
@Singleton
class ActivityRecognitionRegistrar @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val activityRecognitionClient: ActivityRecognitionClient,
) {

    private fun buildTransitionPendingIntent(): PendingIntent {
        val intent = Intent(appContext, ActivityTransitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun buildTransitionRequest(): ActivityTransitionRequest {
        val enterInVehicle = ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()

        val exitInVehicle = ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            .build()

        return ActivityTransitionRequest(listOf(enterInVehicle, exitInVehicle))
    }

    fun register() {
        val pendingIntent = buildTransitionPendingIntent()
        activityRecognitionClient.requestActivityTransitionUpdates(buildTransitionRequest(), pendingIntent)
            .addOnSuccessListener {
                Timber.tag("MT-ActivityRecognition").i("Transition updates registered (IN_VEHICLE ENTER+EXIT)")
            }
            .addOnFailureListener { registrationFailure ->
                Timber.tag("MT-ActivityRecognition").e(registrationFailure, "Failed to register transition updates")
            }
    }

    fun unregister() {
        activityRecognitionClient.removeActivityTransitionUpdates(buildTransitionPendingIntent())
            .addOnSuccessListener {
                Timber.tag("MT-ActivityRecognition").i("Transition updates unregistered")
            }
            .addOnFailureListener { unregisterFailure ->
                Timber.tag("MT-ActivityRecognition").e(unregisterFailure, "Failed to unregister transition updates")
            }
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
