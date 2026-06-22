package com.mileagetracker.app.service.activityrecognition

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers/unregisters the ActivityRecognition transition request. T-001 scaffolding only —
 * the exact `ActivityTransitionRequest` (which transitions, IN_VEHICLE confidence wiring) is
 * geo-sensors-specialist's call (T-002, blueprint open question 1).
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

    /**
     * Registration body intentionally left for T-002 (geo-sensors-specialist): the real
     * ActivityTransitionRequest (transition list, confidence handling) is not yet defined here.
     */
    fun register() {
        buildTransitionPendingIntent()
        // T-002 TODO (geo-sensors-specialist): build ActivityTransitionRequest with the locked
        // 70% start-confidence semantics and call activityRecognitionClient.requestActivityTransitionUpdates(...).
    }

    fun unregister() {
        activityRecognitionClient.removeActivityTransitionUpdates(buildTransitionPendingIntent())
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
